package it.carloni.luca.lgd.spark.step;

import it.carloni.luca.lgd.parameter.step.DataOsservazioneValue;
import it.carloni.luca.lgd.schema.MovimentiSchema;
import it.carloni.luca.lgd.spark.common.AbstractStep;
import org.apache.log4j.Logger;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import scala.collection.Seq;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static it.carloni.luca.lgd.spark.utils.StepUtils.*;

public class Movimenti extends AbstractStep<DataOsservazioneValue> {

    private final Logger logger = Logger.getLogger(getClass());

    @Override
    public void run(DataOsservazioneValue dataOsservazioneValue) {

        String dataOsservazione = dataOsservazioneValue.getDataosservazione();

        logger.info(dataOsservazioneValue.toString());

        String dataOsservazionePattern = getValue("params.dataosservazione.pattern");
        String tlbmovcontaCsv = getValue("movimenti.tlbmovconta.csv");
        String movOutDistPath = getValue("movimenti.mov.out.dist");

        logger.info("params.dataosservazione.pattern: " + dataOsservazionePattern);
        logger.info("movimenti.tlbmovconta.csv: " + tlbmovcontaCsv);
        logger.info("movimenti.mov.out.dist: " + movOutDistPath);

        Dataset<Row> tlbmovconta = readCsvAtPathUsingSchema(tlbmovcontaCsv, MovimentiSchema.getTlbmovcontaPigSchema());

        // FILTER tlbmovconta BY mo_dt_contabile <= $data_osservazione;
        Column dataOsservazioneCol = functions.lit(changeDateFormat(dataOsservazione, dataOsservazionePattern, "yyyyMMdd"));
        Column filterCondition = tlbmovconta.col("mo_dt_contabile").leq(toIntCol(dataOsservazioneCol));

        Map<String, String> selectColMap = new LinkedHashMap<String, String>(){{

            put("mo_istituto", "istituto");
            put("mo_ndg", "ndg");
            put("mo_dt_riferimento", "datariferimento");
            put("mo_sportello", "sportello");
            put("mo_conto_esteso", "conto");
            put("mo_num_soff", "numerosofferenza");
            put("mo_cat_rapp_soff", "catrappsoffer");
            put("mo_fil_rapp_soff", "filrappsoffer");
            put("mo_num_rapp_soff", "numrappsoffer");
            put("mo_id_movimento", "idmovimento");
            put("mo_categoria", "categoria");
            put("mo_causale", "causale");
            put("mo_dt_contabile", "dtcontab");
            put("mo_dt_valuta", "dtvaluta");
            put("mo_imp_movimento", "importo");
            put("mo_flag_extracont", "flagextracontab");
            put("mo_flag_storno", "flagstorno");
        }};

        Dataset<Row> movOutDist = tlbmovconta
                .filter(filterCondition)
                .select(getRenamedColumnSeq(selectColMap))
                .distinct();

        writeDatasetAsCsvAtPath(movOutDist, movOutDistPath);
    }

    private Seq<Column> getRenamedColumnSeq(Map<String, String> renamingMap) {

        List<Column> renamedColumnList = renamingMap.entrySet()
                .stream()
                .map(entry -> functions.col(entry.getKey()).as(entry.getValue()))
                .collect(Collectors.toList());

        return toScalaSeq(renamedColumnList);
    }

}
