package ma.emsi.chidoub.tp4_web_redachidoub.jsf;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import java.io.IOException;

@WebFilter("/*")
public class CharsetFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        // Set UTF-8 encoding for incoming requests
        request.setCharacterEncoding("UTF-8");

        // If you also want to force UTF-8 for responses, uncomment below:
        // response.setCharacterEncoding("UTF-8");
        // response.setContentType("text/html; charset=UTF-8");

        chain.doFilter(request, response);
    }
}
