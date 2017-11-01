/*
 * Copyright (C) open knowledge GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package de.openknowledge.jaxrs.reactive;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;

import javax.inject.Inject;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

/**
 * Reactive implementation of an Java 9 flow API's Publisher to observe ServletInputStream
 * and publishing received bytes to the subscribed Subscribers.
 * TBD backpressure.
 * Adapts a ServletInputStream (>= javax.servlet-api v3.1) and
 * @author Oliver Brüntje - open knowledge GmbH
 * @version 1.0
 *
 */
public class ServletInputStreamPublisherAdapter implements Flow.Publisher<byte[]> {

  private boolean started = false;

  /**
   * The servlet input stream
   */
  private ServletInputStream servletInputStream;

  /**
   * Ther linked list of active subscribers.
   */
  private Map<Flow.Subscriber<? super byte[]>, Flow.Subscription> subscribers;

  /**
   * Constructor
   * @param servletInputStream
   */
  protected ServletInputStreamPublisherAdapter(ServletInputStream servletInputStream) {
    this.servletInputStream = servletInputStream;
    this.subscribers = new HashMap<>();
  }

  /**
   * Starts asynchronous reading from ServletInputStream.
   */
  public void startReading() {
    NoBackpressureReadListener readListener = new NoBackpressureReadListener(servletInputStream, subscribers);

    this.servletInputStream.setReadListener(readListener);
  }

  /**
   * Pushes subscriber to subscribers list.
   * @param subscriber
   */
  @Override public void subscribe(Flow.Subscriber<? super byte[]> subscriber) {
    if (!started) {
      startReading();
      started = true;
    }

    NoBackpressureSubscription subscription = new NoBackpressureSubscription(this);

    this.subscribers.put(subscriber, subscription);

    subscriber.onSubscribe(subscription);
  }

  /**
   * Subscription without backpressure.
   */
  private static class NoBackpressureSubscription implements Flow.Subscription {

    private ServletInputStreamPublisherAdapter adapter;

    /**
     * Constructor
     * @param adapter outer class.
     */
    public NoBackpressureSubscription(ServletInputStreamPublisherAdapter adapter) {
      this.adapter = adapter;
    }

    @Override public void request(long n) {
      // TBD
    }

    /**
     * Removes from subscribers list.
     */
    @Override public void cancel() {
      adapter.subscribers.remove(adapter);
    }
  }

  /**
   * ReadListener implementation iterating of subscribers and publishing available bytes
   * to all subscribers.
   */
  private class NoBackpressureReadListener implements ReadListener {

    /**
     * ServletInputStream to read available data from.
     */
    private final ServletInputStream servletInputStream;

    private final byte[] buffer = new byte[1024];

    private final Map<Flow.Subscriber<? super byte[]>, Flow.Subscription> subscribers;

    /**
     * Constructor
     * @param servletInputStream ServletInputStream to read from.
     * @param subscribers
     */
    public NoBackpressureReadListener(ServletInputStream servletInputStream, Map<Flow.Subscriber<? super byte[]>, Flow.Subscription> subscribers) {
      this.servletInputStream = servletInputStream;
      this.subscribers = subscribers;
    }

    @Override public void onDataAvailable() throws IOException {
      while(servletInputStream.isReady()) {
        try {
          int readBytes = servletInputStream.read(buffer);
          if (readBytes == -1) {
            fireOnCompleted();
            break;
          } else if (readBytes < buffer.length) {
            byte[] chunk = Arrays.copyOf(buffer, readBytes);
            this.subscribers.keySet().forEach(subscriber -> {
              subscriber.onNext(chunk);
            });
          } else {
            this.subscribers.keySet().forEach(subscriber -> {
              subscriber.onNext(buffer);
            });
          }
        } catch (IOException e) {
          fireOnError(e);
          break;
        }
      }
    }

    @Override public void onAllDataRead() throws IOException {
      fireOnCompleted();
    }

    @Override public void onError(Throwable t) {
      fireOnError(t);
    }

    private void fireOnCompleted() {
        this.subscribers.keySet().forEach(subscriber -> {
          subscriber.onComplete();
        });
        subscribers.clear();
    }

    private void fireOnError(Throwable e) {
      this.subscribers.keySet().forEach(subscriber -> {
        subscriber.onError(e);
      });
    }
  }
}
