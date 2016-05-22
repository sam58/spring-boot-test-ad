package com.example.springboot.ad.service;

import com.example.springboot.ad.exceptions.ClientNotAuthenticatedException;
import com.example.springboot.ad.exceptions.InvalidTokenException;
import com.example.springboot.ad.exceptions.TokenAlreadyExistsException;
import com.example.springboot.ad.model.request.UIAuthenticationRequest;
import com.example.springboot.ad.model.security.ADUserDetails;
import com.example.springboot.ad.model.security.AuthToken;
import com.example.springboot.ad.model.security.UserAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ADAuthService {

    /**
     * Authentication Manager from spring security context.
     */
    private AuthenticationManager authenticationManager;

    /**
     * AuthTokenService which validates tokens.
     */
    private AuthTokenService authTokenService;

    @Autowired
    @Qualifier("sessionRegistry")
    private SessionRegistry sessionRegistry;
    /**
     * Constructor injection of AuthenticationManager and AuthTokenService
     */
    @Autowired
    public ADAuthService(AuthenticationManager authenticationManager, AuthTokenService authTokenService) {
        this.authenticationManager = authenticationManager;
        this.authTokenService = authTokenService;
    }

    /**
     * This is where all the magic happens. Authentication request is intercepted by the controller
     * and is sent here for authentication. We invoke authenticationManger to authenticate our user
     * against AD. Once the user is successfully authenticated we set the authentication in the
     * security context so that filters / security can do there thing.
     * <p/>
     * Once authentication is set in the context we generate a AuthToken object with user details taken
     * from the principal. This new generated AuthToken is stored in a tokenStore which ideally could be
     * a caceh or DB.
     *
     * @param request Authentication Request received by the API.
     * @return authToken newly generated token to be sent API consumer.
     * @throws TokenAlreadyExistsException token already exists in the store.
     */

    public AuthToken authenticateUser(UIAuthenticationRequest request) throws TokenAlreadyExistsException {
        final Authentication authentication = authenticationManager.authenticate(
               new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
//        SecurityContextHolder.getContext()
//        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = new ADUserDetails(
                request.getUsername(),
                request.getPassword(),
                authentication.getAuthorities()
        );

        AuthToken authToken = new AuthToken(UUID.randomUUID().toString(), LocalDateTime.now(), userDetails);
        authTokenService.storeToken(authToken);
        return authToken;
    }

    /**
     * Verify if the token is still valid. At the moment the token is only verified for existence and expiry.
     * But all sorts of check can be performed in this method.
     *
     * @param tokenValue value of the token that is being verified.
     * @return Authentication object which holds userDetails.
     */
    public boolean verifyAuthentication(String tokenValue) throws ClientNotAuthenticatedException {
        if (!StringUtils.isEmpty(tokenValue)) {
            final UserDetails userDetails = authTokenService.getToken(tokenValue).getUser();
            if (userDetails != null) {
                Authentication userAuth = new UserAuthentication(userDetails);
                return userAuth.isAuthenticated();
            }
        }
        return false;
    }

    public void logoutUser(String token) throws InvalidTokenException {
        authTokenService.removeToken(token);
    }
}
