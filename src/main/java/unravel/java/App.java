package unravel.java;

import java.io.IOException;
import java.nio.file.Paths;

public class App {
    public static void main(String[] args) throws IOException {
        Analyzer analyzer = new Analyzer();
        analyzer.analyzeFile(Paths.get(args[0]));
        analyzer.writeJson(System.out);
    }
}
