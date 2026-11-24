package archfixture.compliant.adapter.web;

import archfixture.compliant.application.port.in.UseCase;
import org.springframework.web.bind.annotation.RestController;

/** A web adapter: depends on Spring web and on the application layer. */
@RestController
public class Controller {

    private final UseCase useCase;

    public Controller(UseCase useCase) {
        this.useCase = useCase;
    }

    public Object get() {
        return useCase.deposit();
    }
}
