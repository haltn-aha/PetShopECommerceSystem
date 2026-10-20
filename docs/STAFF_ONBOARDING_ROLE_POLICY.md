# UC-21: Cấp tài khoản Staff qua email và chính sách vai trò

Cập nhật 21/10/2026, công việc bắt đầu tối 20/10. Phạm vi quyết định: hoàn thiện quy trình cấp Staff đã có trong UC-21, đồng thời bỏ thao tác đổi role không có nghiệp vụ chuyển đổi cụ thể. Không thêm UC chuyển role.

## 1. Tạo Staff và nhận tài khoản

Tiền điều kiện: Admin đăng nhập; SMTP đã cấu hình; họ tên, email, SĐT hợp lệ và duy nhất. Khách hàng không được tự cấp Staff.

1. Admin mở Quản lý người dùng → Cấp tài khoản nhân viên, nhập họ tên/email/SĐT. Không nhập mật khẩu hoặc chọn role.
2. Hệ thống tạo Users với role STAFF, trạng thái ACTIVE, MustChangePassword=true và hash của bí mật ngẫu nhiên không được đưa cho Admin/Staff. Vì chưa ai biết bí mật này nên Staff chưa thể đăng nhập.
3. Hệ thống tạo token ngẫu nhiên, chỉ lưu SHA-256 vào PasswordResetTokens; hạn 30 phút. Token không nằm trong log hay thông báo Admin.
4. Dùng EmailService được tích hợp từ ft/auth của Bảo để gửi thư HTML có link /reset-password?token=... đến email Staff.
5. Staff mở thư, đặt và xác nhận mật khẩu; hệ thống kiểm tra token còn hạn, chưa dùng, tài khoản ACTIVE và mật khẩu hợp lệ.
6. Lưu hash BCrypt, đánh dấu token đã dùng, làm hết hạn các token còn lại và bỏ cờ MustChangePassword. Staff đăng nhập bằng email và mật khẩu vừa chọn.

SMTP lỗi/thiếu cấu hình: trả lỗi, transaction tạo Users/token được rollback; không báo thành công hoặc hiển thị mật khẩu để chuyển miệng. SMTP nhận thư không bảo đảm thư đã vào Inbox; nếu thư bị chuyển sang Spam hoặc trễ, Admin/Staff kiểm tra hộp thư và dùng quy trình gửi lại.

Trạng thái ACTIVE ở đây là quyền được phép nhận tài khoản; badge Chờ đặt mật khẩu phản ánh MustChangePassword. Không thêm giá trị trạng thái hoặc cột database mới.

## 2. Gửi lại lời mời

- Admin bấm Gửi lại cho Staff ACTIVE đang chờ đặt mật khẩu. Link cũ hết hiệu lực, token mới có hạn 30 phút.
- Không gửi lời mời cho Customer, tài khoản bị khóa hoặc Staff đã thiết lập mật khẩu.
- Nếu SMTP lỗi, rollback việc thay token để link cũ còn hiệu lực trong thời gian còn lại.
- Nếu Admin sửa email, link đã gửi đến email cũ hết hiệu lực; dùng Gửi lại để gửi link đến địa chỉ mới.

Thu hồi token bằng mốc UsedAt (đã dùng hoặc đã bị thu hồi), giữ nguyên ExpiresAt ban đầu. Cách này giữ CHECK ExpiresAt > CreatedAt của schema hiện có, kể cả khi gửi lại ngay trong cùng giây.

## 3. Reset mật khẩu bởi Admin

Admin yêu cầu reset → gửi link qua email → người dùng tự chọn mật khẩu. Khi gửi thành công, mật khẩu cũ bị thay bằng bí mật ngẫu nhiên và session cũ bị thu hồi ở request tiếp theo. Không có mật khẩu tạm trên màn hình. SMTP lỗi thì giữ nguyên mật khẩu và token trước thao tác.

Quên mật khẩu do người dùng tự yêu cầu vẫn gửi link nhưng không vô hiệu hóa mật khẩu đang dùng, để người ngoài không thể làm khóa tài khoản chỉ bằng cách nhập email vào form.

## 4. Vì sao bỏ chuyển Staff ↔ Customer?

UC-21 hiện có cấp tài khoản Staff và quản lý tài khoản; UC-21.1 là khóa/mở. Không có đặc tả điều kiện chuyển vai trò, xử lý chứng từ đang dở, giữ quyền truy cập dữ liệu cá nhân và trách nhiệm lịch sử. Đổi RoleId đơn thuần không đủ diễn tả nghiệp vụ.

| Chuyển đổi | Dữ liệu và vấn đề cần có quy tắc nếu muốn làm sau này |
|---|---|
| Customer → Staff | Orders, Carts, UserAddresses, CustomerVouchers, WishlistItems, Reviews vẫn gắn UserId cũ nhưng Staff mất quyền Customer portal. Cần quy tắc xử lý đơn đang chờ/giao, hủy đơn, địa chỉ và quyền xem lịch sử mua hàng. |
| Staff → Customer | Products.CreatedByUserId/UpdatedByUserId, StockImports.CreatedByUserId, Orders.CancelledByUserId và OrderStatusHistories.ChangedByUserId vẫn là chứng từ công việc của nhân viên cũ. Cần bàn giao công việc, thu hồi quyền và phân biệt vai trò lúc lập chứng từ với vai trò hiện tại. |

Quy tắc áp dụng hiện tại:

- Role cố định theo tài khoản: đăng ký công khai tạo CUSTOMER, Admin cấp tài khoản mới tạo STAFF; không nâng ADMIN tại màn hình sửa.
- Staff nghỉ việc: khóa tài khoản Staff, giữ chứng từ và người thực hiện. Không biến tài khoản thành Customer hoặc chuyển chứng từ sang người khác.
- Customer được tuyển: không tự nâng quyền tài khoản mua hàng hiện có. Cấp tài khoản công việc riêng, thỏa email/SĐT duy nhất; có thể dùng thông tin công việc khác thông tin mua hàng cá nhân.
- Email đã tồn tại thì báo trùng; không tự chuyển vai trò để dùng lại email đó.
- Phần sửa tài khoản chỉ nhận họ tên, email, SĐT, trạng thái. Role hiển thị readonly, không có trường ghi role trong DTO hoặc service. Tham số roleCode/roleId gửi thủ công bị bỏ qua.

Quan hệ FK, UserId và chủ sở hữu đơn/giỏ/địa chỉ/voucher/review/chứng từ được giữ nguyên.

## 5. Tích hợp nhánh Bảo

Repo: https://github.com/vyhncce200061-ops/G03_SWP391. Nguồn EmailService: ft/auth, commit 4233a19d3fdd55fff8bc84dac64235245b8fd4d0.

Tích hợp chọn lọc lớp gửi mail HTML; bổ sung thư mời Staff và đổi xử lý thiếu SMTP thành lỗi, bỏ fallback ghi link vào log. Bản hiện tại giữ đăng nhập/session và token một lần trong bảng đang có. Các phần JWT, ProfileDemoDataInitializer, thay đổi profile/cart của ft/auth không nằm trong lần tích hợp này.

## 6. Cấu hình và demo

Các biến cần cấu hình tại máy chạy ứng dụng, không ghi mật khẩu mail vào Git:

Cách điền trực tiếp khi demo: copy `src/main/resources/application-local.yml.example` thành `application-local.yml`, điền `spring.mail.username` và `spring.mail.password`. Profile dev tự bao gồm local nên Run bình thường sẽ đọc cấu hình riêng. File local bị Git bỏ qua; file example chỉ chứa giá trị trống để chia sẻ.

```text
SMTP_HOST=<máy chủ SMTP>
SMTP_PORT=587
SMTP_USERNAME=<tài khoản gửi thư>
SMTP_PASSWORD=<mật khẩu SMTP/app password>
SMTP_FROM=<địa chỉ gửi, mặc định SMTP_USERNAME>
SMTP_AUTH=true
SMTP_STARTTLS=true
PUBLIC_BASE_URL=http://localhost:8080
```

Cũng nhận MAIL_HOST, MAIL_USERNAME, MAIL_PASSWORD và APP_BASE_URL nếu dùng tên cấu hình này từ nhánh auth. PUBLIC_BASE_URL phải là URL Staff truy cập được; localhost phù hợp khi demo mở thư trên cùng máy.

Demo: Admin tạo Staff → mở hộp thư nhận → bấm link → tự đặt mật khẩu → đăng nhập vào Staff portal → thử mở lại link (bị từ chối). Để demo gửi lại, thực hiện trước khi hoàn tất đặt mật khẩu; link trước phải mất hiệu lực.

Demo role: mở màn hình sửa Staff/Customer, role chỉ đọc; thử POST roleCode khác vẫn giữ role cũ và lịch sử của cùng UserId.

## 7. Database và triển khai

Không thêm migration/cột/bảng trong đợt này. Tận dụng Users.MustChangePassword và PasswordResetTokens đã có từ bản PetShop hiện tại. Máy đang dùng schema cũ từ GitHub main cần đồng bộ các migration trước đây trong database/migrations; đây là điều kiện của bản baseline, không phải schema mới cho gửi mail.

Không gửi email đến Staff thật trong kiểm thử tự động. Test SMTP nhận thư chỉ trên localhost và test nghiệp vụ dùng database thử riêng. Cấu hình SMTP thật và kiểm tra Inbox thực tế là bước cần làm trên máy demo.

Kết quả bản cuối: 44/44 test Maven và 52/52 kiểm tra SQL Server (38 hồi quy + 14 quy trình Staff). Có test giữ nguyên CHECK ExpiresAt > CreatedAt khi gửi lại ngay trong cùng giây, SMTP lỗi rollback tạo/reset, token một lần và POST không đổi role. JAR đã đóng gói thành công.
