/*
 *
 * JsonConverter
 *
 *
 * This document contains trade secret data which is the property of OpenKnowledge GmbH. Information contained herein
 * may not be used, copied or disclosed in whole or part except as permitted by written agreement from open knowledge
 * GmbH.
 *
 * Copyright (C) 2017 open knowledge GmbH / Oldenburg / Germany
 *
 */
package de.openknowledge.jaxrs.reactive.converter;

import de.undercouch.actson.DefaultJsonFeeder;
import de.undercouch.actson.JsonEvent;
import de.undercouch.actson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.Flow;

/**
 * @author Robert Zilke - open knowledge GmbH
 */
public class JsonConverter implements Flow.Processor<byte[], String> {

  private JsonParser jsonParser;

  /**
   * Is equal to the number of removed bytes from byteBuffer
   */
  private int parsedCharactersOffset = 0;

  private Flow.Subscriber<? super String> subscriber;

  private byte[] byteBuffer;

  private int byteBufferPosition = 0;

  private Flow.Subscription subscription;

  private boolean producerCompleted = false;
  private boolean parsingCompleted = false;
  private boolean erred = false;

  private Level nestedObjectLevel;

  private Level nestedArrayLevel;

  /**
   * have to be a field for the case: first bytes contain first bytes of a json object following bytes contain the rest
   */
  private int startOfObjectIndex = 0;

  @SuppressWarnings("WeakerAccess")
  public JsonConverter() {

    jsonParser = new JsonParser(new DefaultJsonFeeder(StandardCharsets.UTF_8));
    byteBuffer = new byte[2048];

    nestedObjectLevel = new Level();
    nestedArrayLevel = new Level();
  }

  // Publisher
  @Override
  public void subscribe(Flow.Subscriber<? super String> subscriber) {

    this.subscriber = subscriber;
  }

  // Subscriber
  @Override
  public void onSubscribe(Flow.Subscription subscription) {

    this.subscription = subscription;
    // later for back pressing
  }

  @Override
  public void onNext(byte[] item) {

    if (erred) {
      return;
    }

    if (producerCompleted && parsingCompleted) {
      throw new IllegalStateException("Processor is completed!");
    }

    boolean status = handleNextBytes(item);
    if (!status) {
      // error occurred
      subscription.cancel();
      return;
    }

    if (!producerCompleted) {
      this.subscription.request(1);
    } else {
      throw new IllegalStateException("Producer is already finished!");
    }
  }

  @Override
  public void onError(Throwable throwable) {

    erred = true;
    subscriber.onError(throwable);
    cleanup();
  }

  @Override
  public void onComplete() {

    producerCompleted = true;

    // todo: be sure to consume last bytes
    // todo what to do if producer told us it has finished, but we need more bytes?
    // todo test if valid data are coming after parsingCompleted
    internalOnComplete();
  }

  private void internalOnComplete() {

    if (!erred && parsingCompleted && producerCompleted) {
      cleanup();
      subscriber.onComplete();
    }
  }

  private void cleanup() {

    jsonParser.getFeeder().done();
    jsonParser = null;
  }

  private void fireJsonError() {

    onError(new IllegalArgumentException("Syntax error in JSON text"));
  }

  private boolean handleNextBytes(byte[] jsonBytes) {

    addToByteBuffer(jsonBytes);

    int jsonBytesPosition = 0; // position in the input JSON text
    int event; // event returned by the parser

    do {
      if (jsonBytesPosition != jsonBytes.length) {
        // provide the parser with more input
        jsonBytesPosition += jsonParser.getFeeder().feed(
            jsonBytes,
            jsonBytesPosition,
            jsonBytes.length - jsonBytesPosition);
      }

      event = jsonParser.nextEvent();

      if (parsingCompleted) {
        if (event == JsonEvent.ERROR) {
          fireJsonError();
          return false;
        }
      } else {
        switch (event) {
          case JsonEvent.START_OBJECT:
            if (nestedObjectLevel.isOnRootLevel()) {
              startOfObjectIndex = jsonParser.getParsedCharacterCount();
            }
            nestedObjectLevel.increment();
            break;
          case JsonEvent.END_OBJECT:
            nestedObjectLevel.decrement();
            if (nestedObjectLevel.isOnRootLevel()) {
              onEndObjectReached();

              // single object, no array
              if (nestedArrayLevel.isOnRootLevel()) {
                parsingCompleted = true;
              }
            }
            break;
          case JsonEvent.START_ARRAY:
            nestedArrayLevel.increment();
            break;
          case JsonEvent.END_ARRAY:
            nestedArrayLevel.decrement();
            if (nestedArrayLevel.isOnRootLevel()) {
              parsingCompleted = true;
            }
            break;
          case JsonEvent.ERROR:
            fireJsonError();
            return false;
          default:
            // nothing
        }
      }

      // do until all jsonBytes consumed and more input needed
    } while (!(jsonBytesPosition == jsonBytes.length && event == JsonEvent.NEED_MORE_INPUT));

    return true;
  }

  private void onEndObjectReached() {

    int endOfObjectIndex = jsonParser.getParsedCharacterCount();

    byte[] bufferedBytes = getBytesInBuffer();

    int startIndexInBuffer = startOfObjectIndex - parsedCharactersOffset - 1;
    int endIndexInBuffer = endOfObjectIndex - parsedCharactersOffset;

    byte[] parsedObjectBytes = Arrays.copyOfRange(bufferedBytes, startIndexInBuffer, endIndexInBuffer);
    subscriber.onNext(new String(parsedObjectBytes));

    // remove json string of found object
    removeBytesFromBuffer(endIndexInBuffer);
  }

  private byte[] getBytesInBuffer() {

    return Arrays.copyOf(byteBuffer, byteBufferPosition);
  }

  private void addToByteBuffer(byte[] bytesToAdd) {

    for (byte byteToAdd : bytesToAdd) {
      byteBuffer[byteBufferPosition++] = byteToAdd;
    }
  }

  /**
   *
   * @param n number of bytes to remove
   */
  private void removeBytesFromBuffer(int n) {

    int start = n;
    if (n + 1 < byteBufferPosition) {
      start++;
    }

    byte[] validBytes = Arrays.copyOfRange(byteBuffer, start, byteBufferPosition);
    byteBufferPosition = 0;
    addToByteBuffer(validBytes);

    parsedCharactersOffset += start;
  }
}
