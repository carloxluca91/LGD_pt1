package steps;

import org.apache.commons.cli.*;
import steps.lgdstep.FrappPuma;

import java.util.logging.Logger;

public class FrappPumaMain {

    private static Logger logger = Logger.getLogger(FrappPumaMain.class.getName());

    public static void main(String[] args) {

        // define option for $data_a
        Option dataAOption = new Option("da", "dataA", true, "parametro $data_a");

        Options options = new Options();
        options.addOption(dataAOption);

        CommandLineParser commandLineParser = new BasicParser();

        String dataA;
        // try to parse and retrieve command line arguments
        try {

            CommandLine commandLine = commandLineParser.parse(options, args);
            dataA = commandLine.getOptionValue("dataA");
            logger.info("Arguments parsed correctly");
        }
        catch (ParseException e) {

            // assign some default values
            logger.info("ParseException: " + e.getMessage());
            dataA = "20190101";
        }

        FrappPuma frappPuma = new FrappPuma(dataA);
        frappPuma.run();
    }
}
