package org.etimoloji.parser;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.util.List;

@RestController
public class ParserController {
    private final Parser parser = new Parser(ParserController.class.getClassLoader().getResourceAsStream("dictionary.txt"));

    public ParserController() throws IOException {

    }

    @GetMapping("/")
    public String test() {
        return "Hello world!";
    }

    @PostMapping("/parse")
    public List<Entry> parseText(@RequestBody String text) {
        return parser.parseDistinct(text, false);
    }
}
