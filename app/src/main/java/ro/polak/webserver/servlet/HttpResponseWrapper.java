/**************************************************
 * Android Web Server
 * Based on JavaLittleWebServer (2008)
 * <p/>
 * Copyright (c) Piotr Polak 2008-2016
 **************************************************/

package ro.polak.webserver.servlet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import ro.polak.utilities.Utilities;
import ro.polak.webserver.Headers;
import ro.polak.webserver.HeadersSerializer;
import ro.polak.webserver.Statistics;
import ro.polak.webserver.WebServer;

/**
 * Represents HTTP response
 *
 * @author Piotr Polak piotr [at] polak [dot] ro
 * @since 200802
 */
public class HttpResponseWrapper implements HttpResponse {

    private Headers headers;
    private OutputStream out;
    private ChunkedPrintWriter printWriter;
    private boolean headersFlushed;
    private List<Cookie> cookies;
    private static Charset charset;
    private static HeadersSerializer headersSerializer;

    static {
        charset = Charset.forName("UTF-8");
        headersSerializer = new HeadersSerializer();
    }

    /**
     * Default constructor.
     */
    public HttpResponseWrapper() {
        headers = new Headers();
        setKeepAlive(false);
        headersFlushed = false;
        cookies = new ArrayList<>();
    }

    /**
     * Creates and returns a response out of the socket
     *
     * @param socket
     * @return
     */
    public static HttpResponseWrapper createFromSocket(Socket socket) throws IOException {
        HttpResponseWrapper response = new HttpResponseWrapper();
        response.out = socket.getOutputStream();
        return response;
    }

    /**
     * Flushes headers, returns false when headers already flushed.
     * <p/>
     * Can be called once per responce, after the fisrt call it "locks"
     *
     * @return true if headers flushed
     * @throws IllegalStateException when headers have been previously flushed.
     * @throws IOException
     */
    private void flushHeaders() throws IllegalStateException, IOException {

        // Prevent from flushing headers more than once
        if (headersFlushed) {
            throw new IllegalStateException("Headers already committed");
        }

        headersFlushed = true;

        for (Cookie cookie : cookies) {
            headers.setHeader(Headers.HEADER_SET_COOKIE, getCookieHeaderValue(cookie));
        }

        serveStream(new ByteArrayInputStream(headersSerializer.serialize(headers).getBytes(charset)), false);
    }

    /**
     * Returns serialized cookie header value.
     *
     * @param cookie
     * @return
     */
    private String getCookieHeaderValue(Cookie cookie) {

        // TODO delegate it to a specialized class
        // TODO test encoding cookie values

        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=").append(Utilities.URLEncode(cookie.getValue()));
        if (cookie.getMaxAge() != -1) {
            String expires = WebServer.sdf.format(new java.util.Date(System.currentTimeMillis() + (cookie.getMaxAge() * 1000)));
            sb.append("; Expires=").append(expires);
        }
        if (cookie.getPath() != null) {
            sb.append("; Path=").append(cookie.getPath());
        }
        if (cookie.getDomain() != null) {
            sb.append("; Domain=").append(cookie.getDomain());
        }
        if (cookie.isHttpOnly()) {
            sb.append("; HttpOnly");
        }
        if (cookie.isSecure()) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    /**
     * Flushes headers and serves the specified file
     *
     * @param file file to be served
     * @throws IOException
     */
    public void serveFile(File file) throws IOException {
        setContentLength(file.length());
        FileInputStream inputStream = new FileInputStream(file);
        serveStream(inputStream);
    }

    /**
     * Server an asset
     *
     * @param inputStream
     * @throws IOException
     */
    public void serveStream(InputStream inputStream) throws IOException {
        serveStream(inputStream, false);
    }

    /**
     * @param inputStream
     * @param flushHeaders
     * @throws IOException
     */
    private void serveStream(InputStream inputStream, boolean flushHeaders) throws IOException {
        // Make sure headers are served before the file content
        // If this throws an IllegalStateException, it means you have tried (incorrectly) to flush headers before
        if (flushHeaders) {
            flushHeaders();
        }

        int numberOfBufferReadBytes;
        byte[] buffer = new byte[512];

        while ((numberOfBufferReadBytes = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, numberOfBufferReadBytes);
            out.flush();

            Statistics.addBytesSend(numberOfBufferReadBytes);
        }
        // Flushing remaining buffer, just in case
        out.flush();

        try {
            inputStream.close();
        } // Closing file input stream
        catch (IOException e) {
        }
    }

    @Override
    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    @Override
    public List<Cookie> getCookies() {
        return cookies;
    }

    @Override
    public boolean isCommitted() {
        return headersFlushed;
    }

    private boolean isTransferChunked() {
        if (!getHeaders().containsHeader(Headers.HEADER_TRANSFER_ENCODING)) {
            return false;
        }

        return getHeaders().getHeader(Headers.HEADER_TRANSFER_ENCODING).toLowerCase().equals("chunked");
    }

    /**
     * Flushes the output
     *
     * @throws IOException
     */
    public void flush() throws IOException {
        // It makes no sense to set chunked encoding if there is no print writer
        if (printWriter != null) {
            if (!getHeaders().containsHeader(Headers.HEADER_TRANSFER_ENCODING) && !getHeaders().containsHeader(Headers.HEADER_CONTENT_LENGTH)) {
                getHeaders().setHeader(Headers.HEADER_TRANSFER_ENCODING, "chunked");
            }
        }

        flushHeaders();

        if (printWriter != null) {
            if (isTransferChunked()) {
                printWriter.writeEnd();
            }
            printWriter.flush();
        }

        out.flush();
    }

    @Override
    public void sendRedirect(String location) {
        headers.setStatus(HttpResponse.STATUS_MOVED_PERMANENTLY);
        headers.setHeader(Headers.HEADER_LOCATION, location);
    }

    @Override
    public void setContentType(String contentType) {
        headers.setHeader(Headers.HEADER_CONTENT_TYPE, contentType);
    }

    @Override
    public String getContentType() {
        return headers.getHeader(Headers.HEADER_CONTENT_TYPE);
    }

    @Override
    public void setKeepAlive(boolean keepAlive) {
        headers.setHeader(Headers.HEADER_CONNECTION, keepAlive ? "keep-alive" : "close");
    }

    @Override
    public void setContentLength(int length) {
        headers.setHeader(Headers.HEADER_CONTENT_LENGTH, "" + length);
    }

    @Override
    public void setContentLength(long length) {
        headers.setHeader(Headers.HEADER_CONTENT_LENGTH, "" + length);
    }

    @Override
    public Headers getHeaders() {
        return headers;
    }

    @Override
    public void setStatus(String status) {
        headers.setStatus(status);
    }

    @Override
    public PrintWriter getPrintWriter() {
        // Creating print writer if it does not exist
        if (printWriter == null) {
            printWriter = new ChunkedPrintWriter(out);
        }

        return printWriter;
    }
}