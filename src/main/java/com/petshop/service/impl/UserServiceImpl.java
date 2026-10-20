package com.petshop.service.impl;

import com.petshop.dto.auth.AddressCreateDto;
import com.petshop.dto.auth.PasswordChangeRequestDto;
import com.petshop.dto.auth.ProfileUpdateRequestDto;
import com.petshop.dto.auth.RegisterRequestDto;
import com.petshop.entity.Role;
import com.petshop.entity.User;
import com.petshop.entity.UserAddress;
import com.petshop.repository.RoleRepository;
import com.petshop.repository.UserAddressRepository;
import com.petshop.repository.UserRepository;
import com.petshop.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final jakarta.persistence.EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng với email: " + email));
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, ProfileUpdateRequestDto request) {
        User user = lockUser(userId);
        user.setFullName(request.getFullName().trim());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phone = request.getPhone().trim();
            userRepository.findByPhone(phone).ifPresent(existing -> {
                if (!existing.getId().equals(userId)) throw new IllegalArgumentException("Số điện thoại đã được dùng bởi tài khoản khác.");
            });
            user.setPhone(phone);
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        userRepository.save(user);
        log.info("Updated profile for user ID: {}", userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, PasswordChangeRequestDto request) {
        User user = lockUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu hiện tại không chính xác.");
        }
        if (!java.util.Objects.equals(request.getNewPassword(), request.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Mật khẩu mới xác nhận không khớp.");
        }
        if (request.getNewPassword() == null || request.getNewPassword().isBlank() || request.getNewPassword().length() < 6 || request.getNewPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Mật khẩu mới tối thiểu 6 ký tự.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        }
        user.setMustChangePassword(false);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Changed password for user ID: {}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserAddress> getCustomerAddresses(Long customerId) {
        return addressRepository.findByCustomerIdAndIsDeletedFalseOrderByIsDefaultDescCreatedAtDesc(customerId);
    }

    @Override
    @Transactional
    public UserAddress addAddress(Long customerId, AddressCreateDto request) {
        User customer = lockUser(customerId);
        boolean isFirst = addressRepository.countByCustomerIdAndIsDeletedFalse(customerId) == 0;
        boolean makeDefault = isFirst || Boolean.TRUE.equals(request.getIsDefault());

        if (makeDefault) {
            addressRepository.resetDefaultAddresses(customerId);
        }

        UserAddress address = UserAddress.builder()
                .customer(customer)
                .recipientName(request.getRecipientName().trim())
                .recipientPhone(request.getRecipientPhone().trim())
                .province(request.getProvince().trim())
                .district(request.getDistrict().trim())
                .ward(request.getWard().trim())
                .addressLine(request.getAddressLine().trim())
                .isDefault(makeDefault)
                .isDeleted(false)
                .build();

        return addressRepository.save(address);
    }

    @Override
    @Transactional
    public void setDefaultAddress(Long customerId, Long addressId) {
        lockUser(customerId);
        UserAddress address = addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ."));
        addressRepository.resetDefaultAddresses(customerId);
        address.setIsDefault(true);
        addressRepository.save(address);
    }

    @Override
    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        lockUser(customerId);
        UserAddress address = addressRepository.findByIdAndCustomerIdAndIsDeletedFalse(addressId, customerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ hợp lệ."));
        address.setIsDeleted(true);
        address.setIsDefault(false);
        addressRepository.save(address);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> getAllUsers(String keyword, String roleCode, String status, Pageable pageable) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        String cleanRoleCode = (roleCode != null && !roleCode.isBlank()) ? roleCode.trim() : null;
        return userRepository.searchUsers(cleanKeyword, cleanRoleCode, (status == null || status.isBlank()) ? null : status.trim().toUpperCase(), pageable);
    }

    @Override
    @Transactional
    public void toggleUserStatus(Long userId, Long adminId) {
        roleRepository.lockAdminRole().orElseThrow(() -> new IllegalStateException("Thiếu vai trò ADMIN."));
        User user = lockUser(userId);
        if ("ACTIVE".equals(user.getStatus()) && "ADMIN".equals(user.getRole().getCode())
                && userRepository.countByRoleCodeAndStatus("ADMIN", "ACTIVE") <= 1) {
            throw new IllegalArgumentException("Phải giữ ít nhất một quản trị viên đang hoạt động.");
        }
        if (user.getId().equals(adminId)) {
            throw new IllegalArgumentException("Không thể tự khóa tài khoản của chính mình.");
        }
        String newStatus = "ACTIVE".equalsIgnoreCase(user.getStatus()) ? "LOCKED" : "ACTIVE";
        user.setStatus(newStatus);
        userRepository.save(user);
        log.info("Admin {} changed status of user {} to {}", adminId, userId, newStatus);
    }

    @Override
    @Transactional
    public User createStaffAccount(RegisterRequestDto request) {
        if (request.getPassword() == null || request.getPassword().isBlank() || request.getPassword().length() < 6 || request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu tối thiểu 6 ký tự và xác nhận phải khớp.");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail().trim())) {
            throw new IllegalArgumentException("Email nhân viên đã tồn tại.");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone().trim())) {
            throw new IllegalArgumentException("Số điện thoại nhân viên đã tồn tại.");
        }

        Role staffRole = roleRepository.findByCode("STAFF")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .code("STAFF")
                        .name("Nhân viên")
                        .createdAt(LocalDateTime.now())
                        .build()));

        User staff = User.builder()
                .fullName(request.getFullName().trim())
                .email(request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null)
                .phone(request.getPhone().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(staffRole)
                .status("ACTIVE")
                .build();

        return userRepository.save(staff);
    }

    @Override
    @Transactional
    public void updateUserByAdmin(Long userId, com.petshop.dto.admin.UserAdminUpdateDto request, Long currentAdminId) {
        roleRepository.lockAdminRole().orElseThrow(() -> new IllegalStateException("Thiếu vai trò ADMIN."));
        User user = lockUser(userId);
        if (!java.util.Set.of("ADMIN", "STAFF", "CUSTOMER").contains(request.getRoleCode())
                || !java.util.Set.of("ACTIVE", "LOCKED").contains(request.getStatus())) {
            throw new IllegalArgumentException("Vai trò hoặc trạng thái không hợp lệ.");
        }
        if ("ACTIVE".equals(user.getStatus()) && "ADMIN".equals(user.getRole().getCode())
                && (!"ACTIVE".equals(request.getStatus()) || !"ADMIN".equals(request.getRoleCode()))
                && userRepository.countByRoleCodeAndStatus("ADMIN", "ACTIVE") <= 1) {
            throw new IllegalArgumentException("Phải giữ ít nhất một quản trị viên đang hoạt động.");
        }

        if (user.getId().equals(currentAdminId) && !"ACTIVE".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalArgumentException("Không thể tự khóa tài khoản của chính mình.");
        }

        if (user.getId().equals(currentAdminId) && !"ADMIN".equalsIgnoreCase(request.getRoleCode())) {
            throw new IllegalArgumentException("Không thể tự hạ cấp quyền quản trị của chính mình.");
        }

        // Check email uniqueness if modified
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            userRepository.findByEmail(newEmail).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("Email '" + newEmail + "' đã được sử dụng bởi tài khoản khác.");
                }
            });
            user.setEmail(newEmail);
        }

        // Check phone uniqueness if modified
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String newPhone = request.getPhone().trim();
            userRepository.findByPhone(newPhone).ifPresent(existing -> {
                if (!existing.getId().equals(user.getId())) {
                    throw new IllegalArgumentException("Số điện thoại '" + newPhone + "' đã được sử dụng bởi tài khoản khác.");
                }
            });
            user.setPhone(newPhone);
        }

        // Update role
        if (!user.getRole().getCode().equalsIgnoreCase(request.getRoleCode())) {
            Role newRole = roleRepository.findByCode(request.getRoleCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Vai trò không hợp lệ: " + request.getRoleCode()));
            user.setRole(newRole);
        }

        user.setFullName(request.getFullName().trim());
        user.setStatus(request.getStatus().trim().toUpperCase());
        userRepository.save(user);
        log.info("Admin {} updated details for user {}", currentAdminId, userId);
    }

    @Override
    @Transactional
    public String resetUserPassword(Long userId, Long currentAdminId) {
        User user = lockUser(userId);
        // Generate a temporary password with 96 bits of cryptographic randomness.
        byte[] randomBytes = new byte[12];
        new java.security.SecureRandom().nextBytes(randomBytes);
        String tempPassword = "Pet@" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        user.setMustChangePassword(true);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        userRepository.save(user);
        log.info("Admin {} reset password for user {}", currentAdminId, userId);
        return tempPassword;
    }

    private User lockUser(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
}
