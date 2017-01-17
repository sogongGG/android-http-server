/**************************************************
 * Android Web Server
 * Based on JavaLittleWebServer (2008)
 * <p/>
 * Copyright (c) Piotr Polak 2008-2017
 **************************************************/

package ro.polak.http.error.impl;

import java.io.IOException;

import ro.polak.http.Headers;
import ro.polak.http.error.AbstractHtmlErrorHandler;
import ro.polak.http.servlet.HttpResponse;

/**
 * 405 Method Not Allowed HTTP error handler
 *
 * @author Piotr Polak piotr [at] polak [dot] ro
 * @since 201509
 */
public class HttpError405Handler extends AbstractHtmlErrorHandler {

    private String allowedMethods;

    public HttpError405Handler(String allowedMethods) {
        super(HttpResponse.STATUS_METHOD_NOT_ALLOWED, "Error 405 - Method Not Allowed",
                "<p>The method specified in the Request-Line is not allowed for the resource " +
                        "identified by the Request-URI.</p>", null);
        this.allowedMethods = allowedMethods;
    }


    @Override
    public void serve(HttpResponse response) throws IOException {
        response.getHeaders().setHeader(Headers.HEADER_ALLOW, allowedMethods);
        super.serve(response);
    }
}
