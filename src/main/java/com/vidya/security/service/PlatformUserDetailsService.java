package com.vidya.security.service;

import com.vidya.security.entity.PlatformUser;
import com.vidya.security.repository.PlatformUserRepository;
import com.vidya.security.user.PlatformUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformUserDetailsService implements UserDetailsService {

    private final PlatformUserRepository userRepository;

    @Override
    @Transactional(readOnly = true, transactionManager = "masterTransactionManager")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        PlatformUser user = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));
        return new PlatformUserDetails(user);
    }
}
