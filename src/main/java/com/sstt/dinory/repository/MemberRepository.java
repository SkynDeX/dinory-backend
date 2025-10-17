package com.sstt.dinory.repository;

import com.sstt.dinory.entity.Member;
import com.sstt.dinory.entity.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    Optional<Member> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    boolean existsByEmail(String email);
}
