package com.example.meetingroom.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 【Entity】Room - 會議室
 *
 * 對應資料庫的 room 表。
 * 欄位說明：
 *   id       - 主鍵，自動遞增
 *   name     - 會議室名稱（如「會議室0304」），唯一值
 *   capacity - 最大容納人數
 *   location - 地點/樓層描述（選填）
 *   active   - 是否啟用（false 表示停用）
 */
@Entity
@Table(name = "room")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 會議室名稱（唯一）
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    // 最大可容納人數
    @Column(nullable = false)
    private Integer capacity;

    // 會議室地點描述
    @Column(length = 100)
    private String location;

    // 是否啟用（預設啟用）
    @Column(nullable = false)
    private Boolean active = true;
}
