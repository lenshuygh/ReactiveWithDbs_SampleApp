package com.example.reservationservice;

import io.r2dbc.spi.ConnectionFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.connectionfactory.R2dbcTransactionManager;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class ReservationServiceApplication {

    @Bean
    ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }

    // functional reactive endpoints
    // this is a buildermethod so can be chained
    @Bean
    RouterFunction<ServerResponse> routes(ReservationRepository reservationRepository) {
        return route()
                .GET("/reservations", serverRequest -> ServerResponse.ok().body(reservationRepository.findAll(), Reservation.class))
                .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }


}
@Configuration
class GreetingsWebsocketConfiguration{

    @Bean
    WebSocketHandler webSocketHandler(GreetingService greetingService){
        return webSocketSession -> {
/*

            Flux<WebSocketMessage> receive = webSocketSession.receive();
            Flux<String> names = receive.map(WebSocketMessage::getPayloadAsText);
            Flux<GreetingRequest> greetingRequestFlux = names.map(GreetingRequest::new);
            Flux<GreetingResponse> greetingResponseFlux = greetingRequestFlux.flatMap(greetingService::greet);
            Flux<String> stringFlux = greetingResponseFlux.map(GreetingResponse::getMessage);
            Flux<WebSocketMessage> map = stringFlux.map(webSocketSession::textMessage);
            return webSocketSession.send(map);
*/

            var receive = webSocketSession.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .map(GreetingRequest::new)
                    .flatMap(greetingService::greet)
                    .map(GreetingResponse::getMessage)
                    .map(webSocketSession::textMessage);
            return webSocketSession.send(receive);
        };
    }

    @Bean
    SimpleUrlHandlerMapping simpleUrlHandlerMapping(WebSocketHandler webSocketHandler){
        return new SimpleUrlHandlerMapping(Map.of("/ws/greetings",webSocketHandler), 10);
    }

    @Bean
    WebSocketHandlerAdapter webSocketHandlerAdapter(){
        return new WebSocketHandlerAdapter();
    }
}


// create a never-ending stream of data
@Service
class GreetingService {
    Flux <GreetingResponse> greet (GreetingRequest greetingRequest){
        return Flux
                .fromStream(Stream.generate(() -> new GreetingResponse("Hello " + greetingRequest.getName() + " @ " + Instant.now() + " ! ")))
                .delayElements(Duration.ofSeconds(1));
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingRequest{
    private String name;
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class GreetingResponse {
    private String message;

}

// a restcontroller that returns a publisher
/*@RestController
@RequiredArgsConstructor
class ReservationRestController {
    private final  ReservationRepository reservationRepository;

    @GetMapping ("/reservations")
    Flux <Reservation> reservationFlux(){
        return this.reservationRepository.findAll();
    }
}*/


@Log4j2
@Component
@RequiredArgsConstructor
class SampleDataInitializer {

    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void Ready() {
        /*
        regular

        Flux<String> names = Flux.just("Josh", "Madhura", "Mark", "Olga", "Spencer", "Ria", "Stéphane", "Violetta");
		Flux<Reservation> reservations = names.map(name -> new Reservation(null,name));
		Flux<Reservation> saved = reservations.flatMap(this.reservationRepository::save);
		*/

        // chained

        var saved = this.reservationService.saveAll("Josh", "Madhura", "Mark", "Olga", "Spencer", "Ria", "Stéphane", "Violetta");


        // everything is deleted async
        // then
        // save everything
        // then
        // ask the DB for the data and
        // log it

        this.reservationRepository
                .deleteAll()
                .thenMany(saved)
                .thenMany(this.reservationRepository.findAll())
                // create a context Key - Value pair : a = b that is visible from within the pipeline
                // this is available from within anywhere in the code -> .doOnEach(signal -> signal.getContext())
                //.subscriberContext(Context.of("a", "b"))

                // move work to other scheduler
                //.subscribeOn(Schedulers.fromExecutor(Executors.newSingleThreadExecutor()))
                .subscribe(log::info);


//		saved.subscribe(log::info);
    }
}


interface ReservationRepository extends ReactiveCrudRepository<Reservation, Integer> {
}

// transactions.. roll back when not succeeded...all was built on threadlocals... in reactive ReactiveTransactions are used
@Service
@RequiredArgsConstructor
class ReservationService {
    private final ReservationRepository reservationRepository;
    private final TransactionalOperator transactionalOperator;

    Flux<Reservation> saveAll(String... names) {

        /*
        normal

        Flux<String> stringFlux = Flux.fromArray(names);
        Flux<Reservation> map = stringFlux
                .map(name -> new Reservation(null, name));
        Flux<Reservation> reservationFlux = map.flatMap(this.reservationRepository::save);
        return reservationFlux.doOnNext(r -> Assert.isTrue(isValid(r),"the name must have a capital first letter"));*/


        // chained
        return
                this.transactionalOperator.transactional(
                        Flux
                                .fromArray(names)
                                .map(name -> new Reservation(null, name))
                                .flatMap(this.reservationRepository::save)
                                .doOnNext(r -> Assert.isTrue(isValid(r), "the name must have a capital first letter"))
                );

    }


    // asserts that each name starts with a capital letter
    private boolean isValid(Reservation reservation) {
        return Character.isUpperCase(reservation.getName().charAt(0));
    }
}

@Data
@AllArgsConstructor
@NoArgsConstructor
class Reservation {

    @Id
    private Integer id;

    private String name;
}