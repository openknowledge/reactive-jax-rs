package de.openknowledge.jaxrs.reactive.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

@ApplicationScoped
public class CustomerRepository {

  private JAXBContext context;
  private Path path;
  
  @PostConstruct
  public void initialize() throws JAXBException, IOException {
    context = JAXBContext.newInstance(Customer.class);
    path = Paths.get("customers.xml");
    if (!Files.exists(path)) {
      Files.createFile(path);
    }
  }

  public void save(Publisher<Customer> customers) throws IOException {
    AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE);

    ByteBuffer buffer = ByteBuffer.allocate(1024);
    buffer.put("<customers>".getBytes());
    buffer.flip();
    fileChannel.write(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {

      @Override
      public void completed(Integer result, ByteBuffer attachment) {
        customers.subscribe(new Subscriber<Customer>() {
          
          private Subscription subscription;

          @Override
          public void onNext(Customer customer) {
            
            try {
              ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
              context.createMarshaller().marshal(customer, outputStream);
              buffer.put(outputStream.toByteArray());
              buffer.flip();
              fileChannel.write(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {

                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                  subscription.request(1);
                }

                @Override
                public void failed(Throwable t, ByteBuffer attachment) {
                  throw new IllegalStateException(t);
                }
              });
            } catch (JAXBException e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public void onComplete() {
            buffer.put("</customers>".getBytes());
            buffer.flip();
            fileChannel.write(buffer, 0);
          }

          @Override
          public void onError(Throwable t) {
            throw new IllegalStateException(t);
          }

          @Override
          public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
          }
        });
      }

      @Override
      public void failed(Throwable t, ByteBuffer attachment) {
        throw new IllegalStateException(t);
      }
    });
  }
}