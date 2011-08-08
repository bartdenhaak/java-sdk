package com.force.sdk.oauth;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.force.sdk.connector.ForceConnectorConfig;
import com.force.sdk.connector.ForceServiceConnector;
import com.force.sdk.oauth.connector.ForceOAuthConnector;
import com.force.sdk.oauth.context.ForceSecurityContextHolder;
import com.force.sdk.oauth.context.SecurityContext;
import com.force.sdk.oauth.context.store.ForceEncryptionException;
import com.force.sdk.oauth.context.store.SecurityContextCookieStore;
import com.force.sdk.oauth.context.store.SecurityContextSessionStore;
import com.force.sdk.oauth.context.store.SecurityContextStorageService;
import com.sforce.ws.ConnectionException;

public class LogoutFilter implements Filter {

	private boolean logoutFromForceCom = false;
	private String logoutTargetUrl = "";
	
	private ForceOAuthConnector oauthConnector;
	private SecurityContextStorageService contextStorageService = null;
	
	static final String CONTEXT_STORE_SESSION_VALUE = "session";
	
	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
        if ("true".equalsIgnoreCase(filterConfig.getInitParameter("logoutFromForceDotCom"))) {
        	logoutFromForceCom = true;
        }
        
        logoutTargetUrl = filterConfig.getInitParameter("logoutSuccessUrl");
        
        if (CONTEXT_STORE_SESSION_VALUE.equals(filterConfig.getInitParameter("securityContextStorageMethod"))) {
        	contextStorageService = new SecurityContextSessionStore();
        } else {
            SecurityContextCookieStore cookieStore = new SecurityContextCookieStore();

            try {
                cookieStore.setKeyFileName(filterConfig.getInitParameter("secure-key-file"));
            } catch (ForceEncryptionException e) {
                throw new ServletException(e);
            }

            contextStorageService = cookieStore;
        }
        
        oauthConnector = new ForceOAuthConnector();
		
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse res = (HttpServletResponse) response;
		
		SecurityContext sc = ForceSecurityContextHolder.get(false);
		
		ForceConnectorConfig config = new ForceConnectorConfig();
        try {
        	config.setServiceEndpoint(sc.getEndPoint());
        	config.setSessionId(sc.getSessionId());
        	ForceServiceConnector connector = new ForceServiceConnector();
        	connector.setConnectorConfig(config);
        	//logout from the partner API
        	connector.getConnection().logout();
        	
        	//clear the security context out of the security context holder
        	ForceSecurityContextHolder.release();
        	
        	contextStorageService.clearSecurityContext(req, res);
        	
        	if(logoutFromForceCom) {
        		logoutTargetUrl = getForceDotComLogoutUrl(req, sc, logoutTargetUrl);
        	}
        	
        	res.sendRedirect(res.encodeRedirectURL(logoutTargetUrl));

        } catch (ConnectionException e) {
            if (config.getSessionId() != null) {
                // If the session id is null that means we visited the renewer method below and the session is dead anyways
                //TODO: log error
            }
        }
		
	}
	
	private String getForceDotComLogoutUrl(
			HttpServletRequest request, SecurityContext sc, String localTargetUrl) {
		
        return oauthConnector.getForceLogoutUrl(request, sc.getEndPoint(), localTargetUrl);
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}

	
}
