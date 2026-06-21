package com.example.meetingroom.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 【Entity】User - 使用者
 *
 * 對應資料庫的 users 表（注意：避開 SQL 保留字 "user"）。
 * 欄位說明：
 *   id         - 主鍵，自動遞增
 *   username   - 使用者姓名（如「梁忠善」）
 *   email      - 電子郵件
 *   employeeNo - 員工編號（如「10010001」）
 *   phone      - 聯絡電話（如「#123」）
 *   department - 所屬部門（如「總務部」）
 *   role       - 角色：USER（一般使用者）/ REVIEWER（審核者）
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 使用者姓名
    @Column(nullable = false, length = 50)
    private String username;

    // 電子郵件（唯一）
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // 員工編號
    @Column(name = "employee_no", length = 20)
    private String employeeNo;

    // 聯絡電話
    @Column(length = 20)
    private String phone;

    // 所屬部門
    @Column(length = 50)
    private String department;

    // 角色：USER 或 REVIEWER
    @Column(nullable = false, length = 20)
    private String role = "USER";

    // BCrypt 雜湊後的密碼
    @Column(nullable = false, length = 100)
    private String password;
}
