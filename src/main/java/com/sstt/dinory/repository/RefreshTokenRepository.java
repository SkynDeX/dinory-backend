package com.sstt.dinory.repository;

import com.sstt.dinory.entity.Member;
import com.sstt.dinory.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByMember(Member member);

    Optional<RefreshToken> findByMemberEmail(String email);

    void deleteByMember(Member member);

    void deleteByToken(String token);
}
