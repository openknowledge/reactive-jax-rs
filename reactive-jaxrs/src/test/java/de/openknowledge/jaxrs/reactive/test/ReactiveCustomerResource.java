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
package de.openknowledge.jaxrs.reactive.test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Publisher;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/reactive/customers")
public class ReactiveCustomerResource {

  @Inject
  private CustomerRepository repository;

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Publisher<Integer> setCustomers(Publisher<Customer> customers) throws IOException {
    return repository.save(customers);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Publisher<Customer> getCustomers() throws IOException {
    return repository.findAllAsync();
  }

  @GET
  @Path("/single")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Customer> getCustomer() {
    CompletableFuture<Customer> future = new CompletableFuture<>();
    future.complete(new Customer("John", "Doe"));
    return future;
  }

}
