/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.astrobotics.ds2017.io;

import org.jboss.netty.buffer.ChannelBuffer;
import org.ros.concurrent.CancellableLoop;
import org.ros.namespace.GraphName;
import org.ros.node.AbstractNodeMain;
import org.ros.node.ConnectedNode;
import org.ros.node.NodeMain;
import org.ros.node.topic.Publisher;

import java.nio.ByteBuffer;

/**
 * A simple {@link Publisher} {@link NodeMain}.
 *
 * @author damonkohler@google.com (Damon Kohler)
 *
 * @Aaron_Brown has edited this code to allow for multi byte arrays
 *
 */
public class Talker extends AbstractNodeMain {
    //Creating a Byte Array
    private byte[] bits = new byte[11];


    //Copy method
    public void getByteMultiArray(byte[] BYTE_ARRAY){
        bits = BYTE_ARRAY;

    }
    private ByteBuffer buf = ByteBuffer.wrap(bits);
    @Override
    public GraphName getDefaultNodeName() {

        return GraphName.of("ROS Android");
    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
        final Publisher<std_msgs.ByteMultiArray> publisher =
                connectedNode.newPublisher("Android Device", std_msgs.ByteMultiArray._TYPE);
        // This CancellableLoop will be canceled automatically when the node shuts
        // down.
        connectedNode.executeCancellableLoop(new CancellableLoop() {
            private int sequenceNumber;

            @Override
            protected void setup() {

                sequenceNumber = 0;
            }

            @Override
            protected void loop() throws InterruptedException {

                std_msgs.ByteMultiArray ByteArray = publisher.newMessage();
                ByteArray.setData(buf); //TODO: Find out how the hell to make a multi byte array work
                publisher.publish(ByteArray);
                sequenceNumber++;
                Thread.sleep(500);
            }
        });
    }
}
