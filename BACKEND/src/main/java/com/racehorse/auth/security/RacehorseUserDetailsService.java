package com.racehorse.auth.security;

import com.racehorse.auth.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class RacehorseUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userRepository;

    public RacehorseUserDetailsService(UserAccountRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Email hoặc mật khẩu không đúng"));
    }
}
