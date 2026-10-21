# Bàn giao bản gộp Admin và ft/auth — 21/10/2026

Nguồn auth: ft/auth, commit 4233a19. Nhánh tích hợp: codex/staff-mail-role-policy. Bản gộp bao gồm các sửa lỗi Admin/Voucher/Reports, toàn vẹn tài khoản/đơn hàng đã làm trước đây, và quy trình Staff qua email.

## Hành vi sau tích hợp

- Customer đăng ký → nhận link xác nhận có hạn 24 giờ → xác nhận mới tạo tài khoản CUSTOMER và giỏ hàng. Link hết hạn/sai/đã xác nhận không tạo thêm tài khoản.
- Đăng nhập giữ Spring Security Form Login, session và CSRF. Token ký của Bảo chỉ dùng cho xác nhận đăng ký. Khóa tài khoản, đổi mật khẩu hoặc thay quyền trong database làm phiên cũ hết hiệu lực ở request tiếp theo.
- Admin tạo Staff bằng họ tên/email/SĐT → gửi lời mời dùng một lần, hạn 30 phút → Staff tự đặt mật khẩu. Không chọn hay đọc mật khẩu Staff trên màn hình Admin.
- Admin hỗ trợ mật khẩu chỉ cho STAFF ACTIVE đã nhận tài khoản; gửi link giữ nguyên mật khẩu/phiên đến khi Staff tự xác nhận mật khẩu mới. Staff chờ dùng Gửi lại; Customer/Admin tự dùng Quên mật khẩu.
- Giữ giao diện hồ sơ/ảnh đại diện, địa chỉ, danh sách/chi tiết đơn và giỏ hàng AJAX của Bảo. Upload avatar kiểm tra nội dung ảnh, giới hạn 5 MB, lưu lại thành PNG với tên do server tạo. File upload lưu dưới uploads/avatars; đó là dữ liệu runtime riêng của máy.
- Đọc trang hồ sơ không tự thêm đơn hoặc địa chỉ mẫu. Danh sách đơn tải sẵn các dòng hàng của trang hiện tại trong transaction riêng, giữ đúng phân trang SQL và tránh lỗi LazyInitializationException khi render giao diện.
- Quyền CUSTOMER/STAFF cố định theo tài khoản; không có chuyển role từ màn hình quản lý.

## Những phần đã dọn

Xóa 146 file trong src/test/main vì đây là bản sao source/resource cũ, không nằm trong nguồn test Maven. Xóa test.iml và application-test.yml trùng trong src/main/resources. Xóa bộ tự tạo dữ liệu ProfileDemoDataInitializer và filter cookie JWT không dùng. Giữ 6 lớp test đang chạy cùng src/test/resources/application-test.yml.

## Cấu hình và database

application-local.yml trên máy đã được đổi sang SMTP Bảo cung cấp. Xác thực SMTP và STARTTLS thành công, không gửi mail thật trong phép thử kết nối. File này chứa bí mật riêng, bị loại khỏi Git và JAR; không gửi kèm source cho nhóm. Thành viên khác tự tạo file từ application-local.yml.example hoặc dùng biến môi trường MAIL_USERNAME/MAIL_PASSWORD (cũng nhận SMTP_USERNAME/SMTP_PASSWORD).

JWT_SECRET hoặc app.jwt.secret trong file local cần là chuỗi ngẫu nhiên ít nhất 32 ký tự để link đăng ký sống qua lần restart. Máy hiện tại đã có khóa riêng. Nếu để trống, ứng dụng dùng khóa tạm trong bộ nhớ; restart làm link đăng ký trước đó mất hiệu lực. Không chia sẻ một khóa mẫu cố định.

Không thêm migration/cột/bảng trong lần gộp auth này. GitHub ft/auth gốc còn dùng baseline schema cũ: trước khi chạy bản gộp, nhóm cần dùng database bootstrap đã sửa hoặc áp dụng các migration đã bàn giao trước đây trong database/migrations theo README. Không chạy script tạo lại database lên dữ liệu cần giữ. Những migration này thuộc các sửa lỗi trước lần gộp hiện tại.

## Kiểm chứng và demo

Kết quả test/build lưu tại outputs/auth_integration_20261021 của workspace làm việc; bản source chia sẻ chỉ chứa test Maven có thể chạy lại, không chứa log/private config.

53/53 test Maven đạt; JAR đóng gói thành công. 57/57 kiểm tra SQL Server đạt trên database thử (38 hồi quy nghiệp vụ/giao diện + 19 xác nhận email/Staff). Có kiểm tra ngoài transaction phát hiện và xác nhận đã sửa lỗi tải Order.items khi mở hồ sơ có đơn. SMTP thật đã xác thực thành công bằng STARTTLS; chưa gửi mail đến người nhận thật trong lần kiểm thử này.

Demo theo thứ tự: đăng ký Customer và xác nhận email → đăng nhập → xem/sửa hồ sơ → thêm địa chỉ và giỏ hàng → xem đơn đã có. Với Admin: cấp Staff → Staff nhận thư/đặt mật khẩu → đăng nhập Staff → gửi link hỗ trợ và xác nhận rằng phiên cũ chỉ bị thu hồi sau khi Staff đặt mật khẩu mới. Đường link localhost phải được mở trên máy đang chạy ứng dụng.
