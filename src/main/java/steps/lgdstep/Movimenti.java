package steps.lgdstep;

import org.apache.log4j.Logger;
import org.apache.spark.sql.*;
import scala.collection.Seq;
import steps.abstractstep.AbstractStep;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Movimenti extends AbstractStep {

    // required parameter
    private String dataOsservazione;

    public Movimenti(String loggerName, String dataOsservazione){

        super(loggerName);
        logger = Logger.getLogger(loggerName);

        this.dataOsservazione = dataOsservazione;

        stepInputDir = getLGDPropertyValue("movimenti.input.dir");
        stepOutputDir = getLGDPropertyValue("movimenti.output.dir");

        logger.debug("stepInputDir: " + stepInputDir);
        logger.debug("stepOutputDir: " + stepOutputDir);
        logger.debug("dataOsservazione: " + this.dataOsservazione);
    }

    @Override
    public void run() {

        String csvFormat = getLGDPropertyValue("csv.format");
        String tlbmovcontaCsv = getLGDPropertyValue("tlbmovconta.csv");

        logger.debug("csvFormat: " + csvFormat);
        logger.debug("tlbmovcontaCsv: " + tlbmovcontaCsv);

        List<String> tlbmovcontaColumnNames = Arrays.asList("mo_dt_riferimento", "mo_istituto", "mo_ndg", "mo_sportello",
                "mo_conto", "mo_conto_esteso", "mo_num_soff", "mo_cat_rapp_soff", "mo_fil_rapp_soff", "mo_num_rapp_soff",
                "mo_id_movimento", "mo_categoria", "mo_causale", "mo_dt_contabile", "mo_dt_valuta", "mo_imp_movimento",
                "mo_flag_extracont", "mo_flag_storno", "mo_ndg_principale", "mo_dt_inizio_ciclo");

        Dataset<Row> tlbmovconta = sparkSession.read().format(csvFormat).option("delimiter", ",")
                .schema(getStringTypeSchema(tlbmovcontaColumnNames)).csv(Paths.get(stepInputDir, tlbmovcontaCsv).toString());

        // FILTER tlbmovconta BY mo_dt_contabile <= $data_osservazione;
        String dataOsservazionePattern = getLGDPropertyValue("params.dataosservazione.pattern");
        Column dataOsservazioneCol = functions.lit(changeDateFormat(dataOsservazione, dataOsservazionePattern, "yyyyMMdd"));
        Column filterCondition = tlbmovconta.col("mo_dt_contabile").leq(dataOsservazioneCol);

        Map<String, String> selectColMap = new HashMap<>();
        selectColMap.put("mo_istituto", "istituto");
        selectColMap.put("mo_ndg", "ndg");
        selectColMap.put("mo_dt_riferimento", "datariferimento");
        selectColMap.put("mo_sportello", "sportello");
        selectColMap.put("mo_conto_esteso", "conto");
        selectColMap.put("mo_num_soff", "numerosofferenza");
        selectColMap.put("mo_cat_rapp_soff", "catrappsoffer");
        selectColMap.put("mo_fil_rapp_soff", "filrappsoffer");
        selectColMap.put("mo_num_rapp_soff", "numrappsoffer");
        selectColMap.put("mo_id_movimento", "idmovimento");
        selectColMap.put("mo_categoria", "categoria");
        selectColMap.put("mo_causale", "causale");
        selectColMap.put("mo_dt_contabile", "dtcontab");
        selectColMap.put("mo_dt_valuta", "dtvaluta");
        selectColMap.put("mo_imp_movimento", "importo");
        selectColMap.put("mo_flag_extracont", "flagextracontab");
        selectColMap.put("mo_flag_storno", "flagstorno");

        Seq<Column> selectColSeq = toScalaColSeq(selectDfColumns(tlbmovconta, selectColMap));
        Dataset<Row> movOutDist = tlbmovconta.filter(filterCondition).select(selectColSeq).distinct();

        String movOutDistPath = getLGDPropertyValue("mov.out.dist");
        logger.debug("movOutDistPath: " + movOutDistPath);

        movOutDist.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(
                Paths.get(stepOutputDir, movOutDistPath).toString());

    }
}
