/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.testing.async;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Test;

public abstract class AbstractHttpAsyncRedirectsTest <T extends CloseableHttpAsyncClient> extends AbstractIntegrationTestBase<T> {

    protected final HttpVersion version;

    public AbstractHttpAsyncRedirectsTest(final HttpVersion version, final URIScheme scheme) {
        super(scheme);
        this.version = version;
    }

    @Override
    public final HttpHost start() throws Exception {
        if (version.greaterEquals(HttpVersion.HTTP_2)) {
            return super.start(null, H2Config.DEFAULT);
        } else {
            return super.start(null, Http1Config.DEFAULT);
        }
    }

    static class BasicRedirectService extends AbstractSimpleServerExchangeHandler {

        private final int statuscode;

        public BasicRedirectService(final int statuscode) {
            super();
            this.statuscode = statuscode;
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/oldlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(statuscode);
                    response.addHeader(new BasicHeader("Location",
                            new URIBuilder(requestURI).setPath("/newlocation/").build()));
                    return response;
                } else if (path.equals("/newlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                    response.setBody("Successful redirect", ContentType.TEXT_PLAIN);
                    return response;
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    static class CircularRedirectService extends AbstractSimpleServerExchangeHandler {

        public CircularRedirectService() {
            super();
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.startsWith("/circular-oldlocation")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/circular-location2"));
                    return response;
                } else if (path.startsWith("/circular-location2")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/circular-oldlocation"));
                    return response;
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    static class RelativeRedirectService extends AbstractSimpleServerExchangeHandler {

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/oldlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/relativelocation/"));
                    return response;
                } else if (path.equals("/relativelocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                    response.setBody("Successful redirect", ContentType.TEXT_PLAIN);
                    return response;
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }
    }

    static class RelativeRedirectService2 extends AbstractSimpleServerExchangeHandler {

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/test/oldlocation")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "relativelocation"));
                    return response;
                } else if (path.equals("/test/relativelocation")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                    response.setBody("Successful redirect", ContentType.TEXT_PLAIN);
                    return response;
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    @Test
    public void testBasicRedirect300() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MULTIPLE_CHOICES);
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect301() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MOVED_PERMANENTLY);
            }

        });

        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testBasicRedirect302() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MOVED_TEMPORARILY);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testBasicRedirect302NoLocation() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
                        return new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    }

                };
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();
        Assert.assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testBasicRedirect303() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_SEE_OTHER);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testBasicRedirect304() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_NOT_MODIFIED);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_NOT_MODIFIED, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect305() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_USE_PROXY);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_USE_PROXY, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect307() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_TEMPORARY_REDIRECT);
            }

        });
        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test(expected=ExecutionException.class)
    public void testMaxRedirectCheck() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new CircularRedirectService();
            }

        });
        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(5).build();
        try {
            final SimpleHttpRequest request = SimpleHttpRequests.get(target, "/circular-oldlocation/");
            request.setConfig(config);
            final Future<SimpleHttpResponse> future = httpclient.execute(request, null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof RedirectException);
            throw e;
        }
    }

    @Test(expected=ExecutionException.class)
    public void testCircularRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new CircularRedirectService();
            }

        });
        final HttpHost target = start();

        final RequestConfig config = RequestConfig.custom()
                .setCircularRedirectsAllowed(false)
                .build();
        try {
            final SimpleHttpRequest request = SimpleHttpRequests.get(target, "/circular-oldlocation/");
            request.setConfig(config);
            final Future<SimpleHttpResponse> future = httpclient.execute(request, null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof CircularRedirectException);
            throw e;
        }
    }

    @Test
    public void testPostRedirectSeeOther() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_SEE_OTHER);
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final SimpleHttpRequest post = SimpleHttpRequests.post(target, "/oldlocation/");
        post.setBody("stuff", ContentType.TEXT_PLAIN);
        final Future<SimpleHttpResponse> future = httpclient.execute(post, context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals("GET", request.getMethod());
    }

    @Test
    public void testRelativeRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RelativeRedirectService();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/relativelocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testRelativeRedirect2() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RelativeRedirectService2();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/test/oldlocation"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/test/relativelocation", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    static class BogusRedirectService extends AbstractSimpleServerExchangeHandler {

        private final String url;

        public BogusRedirectService(final String url) {
            super();
            this.url = url;
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/oldlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", url));
                    return response;
                } else if (path.equals("/relativelocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                    response.setBody("Successful redirect", ContentType.TEXT_PLAIN);
                    return response;
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    @Test(expected=ExecutionException.class)
    public void testRejectBogusRedirectLocation() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BogusRedirectService("xxx://bogus");
            }

        });
        final HttpHost target = start();

        try {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequests.get(target, "/oldlocation/"), null);
            future.get();
        } catch (final ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof HttpException);
            throw ex;
        }
    }

    @Test(expected=ExecutionException.class)
    public void testRejectInvalidRedirectLocation() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BogusRedirectService("/newlocation/?p=I have spaces");
            }

        });
        final HttpHost target = start();

        try {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequests.get(target, "/oldlocation/"), null);
            future.get();
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause() instanceof ProtocolException);
            throw e;
        }
    }

    @Test
    public void testRedirectWithCookie() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new BasicRedirectService(HttpStatus.SC_MOVED_TEMPORARILY);
            }

        });
        final HttpHost target = start();

        final CookieStore cookieStore = new BasicCookieStore();
        final HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        final BasicClientCookie cookie = new BasicClientCookie("name", "value");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");

        cookieStore.addCookie(cookie);

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());

        final Header[] headers = request.getHeaders("Cookie");
        Assert.assertEquals("There can only be one (cookie)", 1, headers.length);
    }

    static class CrossSiteRedirectService extends AbstractSimpleServerExchangeHandler {

        private final HttpHost host;

        public CrossSiteRedirectService(final HttpHost host) {
            super();
            this.host = host;
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            final String location;
            try {
                final URIBuilder uribuilder = new URIBuilder(request.getUri());
                uribuilder.setScheme(host.getSchemeName());
                uribuilder.setHost(host.getHostName());
                uribuilder.setPort(host.getPort());
                uribuilder.setPath("/random/1024");
                location = uribuilder.build().toASCIIString();
            } catch (final URISyntaxException ex) {
                throw new ProtocolException("Invalid request URI", ex);
            }
            final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_TEMPORARY_REDIRECT);
            response.addHeader(new BasicHeader("Location", location));
            return response;
        }
    }

    @Test
    public void testCrossSiteRedirect() throws Exception {
        server.register("/random/*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncRandomHandler();
            }

        });
        final HttpHost redirectTarget = start();

        final H2TestServer secondServer = new H2TestServer(IOReactorConfig.DEFAULT,
                scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null, null, null);
        try {
            secondServer.register("/redirect/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    return new CrossSiteRedirectService(redirectTarget);
                }

            });

            if (version.greaterEquals(HttpVersion.HTTP_2)) {
                secondServer.start(H2Config.DEFAULT);
            } else {
                secondServer.start(Http1Config.DEFAULT);
            }
            final Future<ListenerEndpoint> endpointFuture = secondServer.listen(new InetSocketAddress(0));
            final ListenerEndpoint endpoint2 = endpointFuture.get();

            final InetSocketAddress address2 = (InetSocketAddress) endpoint2.getAddress();
            final HttpHost initialTarget = new HttpHost(scheme.name(), "localhost", address2.getPort());

            final Queue<Future<SimpleHttpResponse>> queue = new ConcurrentLinkedQueue<>();
            for (int i = 0; i < 1; i++) {
                queue.add(httpclient.execute(SimpleHttpRequests.get(initialTarget, "/redirect/anywhere"), null));
            }
            while (!queue.isEmpty()) {
                final Future<SimpleHttpResponse> future = queue.remove();
                final HttpResponse response = future.get();
                Assert.assertNotNull(response);
                Assert.assertEquals(200, response.getCode());
            }
        } finally {
            server.shutdown(TimeValue.ofSeconds(5));
        }
    }

    private static class RomeRedirectService extends AbstractSimpleServerExchangeHandler {

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/rome")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                    response.setBody("Successful redirect", ContentType.TEXT_PLAIN);
                    return response;
                } else {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
                    response.addHeader(new BasicHeader("Location", "/rome"));
                    return response;
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    @Test
    public void testRepeatRequest() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RomeRedirectService();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future1 = httpclient.execute(
                SimpleHttpRequests.get(target, "/rome"), context, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);

        final Future<SimpleHttpResponse> future2 = httpclient.execute(
                SimpleHttpRequests.get(target, "/rome"), context, null);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertEquals("/rome", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testRepeatRequestRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RomeRedirectService();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future1 = httpclient.execute(
                SimpleHttpRequests.get(target, "/lille"), context, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);

        final Future<SimpleHttpResponse> future2 = httpclient.execute(
                SimpleHttpRequests.get(target, "/lille"), context, null);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertEquals("/rome", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testDifferentRequestSameRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new RomeRedirectService();
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future1 = httpclient.execute(
                SimpleHttpRequests.get(target, "/alian"), context, null);
        final HttpResponse response1 = future1.get();
        Assert.assertNotNull(response1);

        final Future<SimpleHttpResponse> future2 = httpclient.execute(
                SimpleHttpRequests.get(target, "/lille"), context, null);
        final HttpResponse response2 = future2.get();
        Assert.assertNotNull(response2);


        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response2.getCode());
        Assert.assertEquals("/rome", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

}
