//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.javax.tests.server;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.websocket.OnMessage;
import javax.websocket.server.ServerEndpoint;

import org.eclipse.jetty.toolchain.test.jupiter.WorkDir;
import org.eclipse.jetty.toolchain.test.jupiter.WorkDirExtension;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.websocket.core.Frame;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.OpCode;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;
import org.eclipse.jetty.websocket.javax.tests.WSServer;
import org.eclipse.jetty.websocket.javax.tests.framehandlers.FrameHandlerTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test Echo of Large messages, targeting the {@link javax.websocket.Session#setMaxTextMessageBufferSize(int)} functionality
 */
@ExtendWith(WorkDirExtension.class)
public class LargeAnnotatedTest
{
    @ServerEndpoint(value = "/echo/large")
    public static class LargeEchoConfiguredSocket
    {
        @OnMessage(maxMessageSize = 128 * 1024)
        public String echo(String msg)
        {
            return msg;
        }
    }

    public WorkDir testdir;

    @Test
    public void testEcho() throws Exception
    {
        WSServer wsb = new WSServer(testdir.getPath(), "app");
        wsb.createWebInf();
        wsb.copyEndpoint(LargeEchoConfiguredSocket.class);

        try
        {
            wsb.start();
            URI uri = wsb.getWsUri();

            WebAppContext webapp = wsb.createWebAppContext();
            wsb.deployWebapp(webapp);

            WebSocketCoreClient client = new WebSocketCoreClient();
            try
            {
                client.start();

                FrameHandlerTracker clientSocket = new FrameHandlerTracker();
                clientSocket.setMaxTextMessageSize(128 * 1024);

                Future<FrameHandler.CoreSession> clientConnectFuture = client.connect(clientSocket, uri.resolve("/app/echo/large"));
                // wait for connect
                FrameHandler.CoreSession coreSession = clientConnectFuture.get(1, TimeUnit.SECONDS);
                try
                {

                    // The message size should be bigger than default, but smaller than the limit that LargeEchoSocket specifies
                    byte txt[] = new byte[100 * 1024];
                    Arrays.fill(txt, (byte)'o');
                    String msg = new String(txt, StandardCharsets.UTF_8);
                    coreSession.sendFrame(new Frame(OpCode.TEXT).setPayload(msg), Callback.NOOP, false);

                    // Receive echo
                    String incomingMessage = clientSocket.messageQueue.poll(5, TimeUnit.SECONDS);
                    assertThat("Expected message", incomingMessage, is(msg));
                }
                finally
                {
                    coreSession.close(Callback.NOOP);
                }
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            wsb.stop();
        }
    }
}