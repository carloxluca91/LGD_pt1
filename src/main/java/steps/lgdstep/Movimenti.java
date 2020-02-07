package steps.lgdstep;

import org.apache.log4j.Logger;
import org.apache.spark.sql.*;
import scala.collection.Seq;
import steps.abstractstep.AbstractStep;
import steps.abstractstep.StepUtils;
import steps.schemas.MovimentiSchema;

import java.util.HashMap;
import java.util.Map;

import static steps.abstractstep.StepUtils.*;

public class Movimenti extends AbstractStep {

    // required parameter
    private String dataOsservazione;

    public Movimenti(String dataOsservazione){

        logger = Logger.getLogger(Movimenti.class);

        this.dataOsservazione = dataOsservazione;
        stepInputDir = getLGDPropertyValue("movimenti.input.dir");
        stepOutputDir = getLGDPropertyValue("movimenti.output.dir");

        logger.debug("dataOsservazione: " + this.dataOsservazione);
        logger.debug("stepInputDir: " + stepInputDir);
        logger.debug("stepOutputDir: " + stepOutputDir);
    }

    @Override
    public void run() {

        String dataOsservazionePattern = getLGDPropertyValue("params.dataosservazione.pattern");
        String tlbmovcontaCsv = getLGDPropertyValue("movimenti.tlbmovconta.csv");
        String movOutDistPath = getLGDPropertyValue("movimenti.mov.out.dist");

        logger.debug("dataOsservazionePattern: " + dataOsservazionePattern);
        logger.debug("tlbmovcontaCsv: " + tlbmovcontaCsv);
        logger.debug("movOutDistPath: " + movOutDistPath);

        Dataset<Row> tlbmovconta = sparkSession.read().format(csvFormat).option("delimiter", ",")
                .schema(StepUtils.fromPigSchemaToStructType(MovimentiSchema.getTlbmovcontaPigSchema()))
                .csv(tlbmovcontaCsv);

        // FILTER tlbmovconta BY mo_dt_contabile <= $data_osservazione;
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

        movOutDist.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(movOutDistPath);

    }
}