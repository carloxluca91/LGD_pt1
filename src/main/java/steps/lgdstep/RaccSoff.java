package steps.lgdstep;

import org.apache.log4j.Logger;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import scala.collection.Seq;
import steps.abstractstep.AbstractStep;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaccSoff extends AbstractStep {

    public RaccSoff(String loggerName){

        super(loggerName);
        logger = Logger.getLogger(loggerName);

        stepInputDir = getLGDPropertyValue("racc.soff.input.dir");
        stepOutputDir = getLGDPropertyValue("racc.soff.output.dir");

        logger.debug("stepInputDir: " + stepInputDir);
        logger.debug("stepOutputDir: " + stepOutputDir);
    }

    @Override
    public void run() {

        String csvFormat = getLGDPropertyValue("csv-format");
        String dblabCsvPath = getLGDPropertyValue("dblabtlbxd9.path.csv");

        logger.debug("csvFormat: " + csvFormat);
        logger.debug("dblabCsvPath: " + dblabCsvPath);

        List<String> dllabColumnNames = Arrays.asList(
                "istricsof", "ndgricsof", "numricsof", "istcedsof", "ndgcedsof", "numcedsof", "data_primo_fine_me");
        Dataset<Row> dllab = sparkSession.read().format(csvFormat).option("delimiter", ",")
                .schema(getStringTypeSchema(dllabColumnNames))
                .csv(Paths.get(stepInputDir, dblabCsvPath).toString());

        Map<String, String> columnMap = new HashMap<>();
        columnMap.put("istricsof", "IST_RIC_SOF");
        columnMap.put("ndgricsof", "NDG_RIC_SOF");
        columnMap.put("numricsof", "NUM_RIC_SOF");
        columnMap.put("istcedsof", "IST_CED_SOF");
        columnMap.put("ndgcedsof", "NDG_CED_SOF");
        columnMap.put("numcedsof", "NUM_CED_SOF");
        columnMap.put("data_primo_fine_me", "DATA_FINE_PRIMO_MESE_RIC");

        Seq<Column> dllabSelectSeq = toScalaColSeq(selectDfColumns(dllab, columnMap));

        dllab.select(dllabSelectSeq).write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(stepOutputDir);

    }
}
