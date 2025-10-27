package com.sstt.dinory.domain.child.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.sstt.dinory.domain.auth.entity.Member;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "child")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Child {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; 

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 10)
    private String gender;

//    @Type(JsonType.class)
//    @Column(name = "interests", columnDefinition = "json")
//    private List<String> interests;
//
//    @Type(JsonType.class)
//    @Column(name = "concerns", columnDefinition = "json")
//    private List<String> concerns;

//    @JdbcTypeCode(SqlTypes.JSON)
//    @Column(name = "interests", columnDefinition = "json")
//    private List<String> interests;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "concerns", columnDefinition = "json")
    private List<String> concerns;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
