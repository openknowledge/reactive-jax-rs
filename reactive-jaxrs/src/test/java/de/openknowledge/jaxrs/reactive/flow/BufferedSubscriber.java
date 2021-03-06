package de.openknowledge.jaxrs.reactive.flow;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

/**
 * Adapter delegating between Flow.Subscriber and Consumer.
 * @param <T> Delegated type.
 */
public class BufferedSubscriber<T> implements Flow.Subscriber<T> {

  private LinkedList<T> list;

  private boolean completed = false;

  private Throwable exception;

  /**
   * Constructor.
   */
  public BufferedSubscriber() {
    this.list = new LinkedList<>();
  }

  @Override public void onSubscribe(Flow.Subscription subscription) {

  }

  @Override public void onError(Throwable throwable) {
    this.exception = throwable;
  }

  @Override public void onComplete() {
    this.completed = true;
  }

  @Override public void onNext(T item) {
    this.list.push(item);
  }

  /**
   * Copies current received items to list,
   * @return Copied list.
   */
  public List<T> toList() {
    return this.list.stream().collect(Collectors.toList());
  }

  /**
   * Returns true of completed has been called. False else.
   * @return See description.
   */
  public boolean isCompleted() {
    return completed;
  }

  /**
   * Gets exception received by onError().
   * @return Can be null of no exception has been received until now.
   */
  public Throwable getException() {
    return exception;
  }
}
