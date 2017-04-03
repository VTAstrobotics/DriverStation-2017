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

import std_msgs.String;

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


    @Override
    public GraphName getDefaultNodeName() {

        return GraphName.of("ds2017");
    }

    @Override
    public void onStart(final ConnectedNode connectedNode) {
        final Publisher<std_msgs.String> publisher =
                connectedNode.newPublisher("/robot/teleop", std_msgs.String._TYPE);
        // This CancellableLoop will be canceled automatically when the node shuts
        // down.
        connectedNode.executeCancellableLoop(new CancellableLoop() {
            private int sequenceNumber;//Declares Counter That tells how many msgs have been sent

            @Override
            protected void setup() {

                sequenceNumber = 0;//Initalizes counter to Zero First run through
            }
            std_msgs.String setData(java.lang.String String){
                std_msgs.String str = publisher.newMessage();//This inializes the String Message
                str.setData(String);
                return str;
            }
            @Override
            protected void loop() throws InterruptedException {
                publisher.publish(setData("Crap"));//Publishes the set string
                sequenceNumber++;
                Thread.sleep(80);
            }
        });
    }
}
