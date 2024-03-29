package com.example.reservationservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Tailable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

@SpringBootApplication
public class ReservationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }


}

@Log4j2
@Component
@RequiredArgsConstructor
class SampleDataInitializer {

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

        var saved = Flux
                .just("Josh", "Madhura", "Mark", "Olga", "Spencer", "Ria", "Stéphane", "Violetta")
                .map(name -> new Reservation(null, name))
                .flatMap(this.reservationRepository::save);


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
                .subscribe(log::info);


//		saved.subscribe(log::info);
    }
}


interface ReservationRepository extends ReactiveCrudRepository<Reservation, String> {
    /*
    if mongoDB is in clustermode and it gets the name below it returns the query below
    @Tailable
    Flux<Reservation> findByName(String name);
    */


}

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
class Reservation {

    @Id
    private String id;

    private String name;
}