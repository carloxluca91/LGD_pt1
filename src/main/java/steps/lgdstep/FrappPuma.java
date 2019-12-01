package steps.lgdstep;

import org.apache.log4j.Logger;
import org.apache.spark.sql.*;
import scala.collection.Seq;
import steps.abstractstep.AbstractStep;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FrappPuma extends AbstractStep {

    // required parameters
    private String dataA;

    public FrappPuma(String loggerName, String dataA){

        super(loggerName);

        logger = Logger.getLogger(loggerName);

        this.dataA = dataA;

        stepInputDir = getPropertyValue("frapp.puma.input.dir");
        stepOutputDir = getPropertyValue("frapp.puma.output.dir");

        logger.debug("stepInputDir: " + stepInputDir);
        logger.debug("stepOutputDir: " + stepOutputDir);
        logger.debug("dataA: " + this.dataA);
    }

    @Override
    public void run() {

        String csvFormat = getPropertyValue("csv.format");
        String cicliNdgPath = getPropertyValue("cicli.ndg.path.csv");
        String tlbgaranPath = getPropertyValue("tlbgaran.path");

        logger.debug("csvFormat: " + csvFormat);
        logger.debug("cicliNdgPath: " + cicliNdgPath);
        logger.debug("tlbgaranPath:" + tlbgaranPath);

        // 22
        List<String> tlbcidefColumns = Arrays.asList("codicebanca", "ndgprincipale", "datainiziodef", "datafinedef",
                "datainiziopd", "datainizioristrutt", "datainizioinc", "datainiziosoff", "c_key", "tipo_segmne", "sae_segm",
                "rae_segm", "segmento", "tp_ndg", "provincia_segm", "databilseg", "strbilseg", "attivobilseg", "fatturbilseg",
                "ndg_collegato", "codicebanca_collegato", "cd_collegamento", "cd_fiscale", "dt_rif_udct");

        Dataset<Row> tlbcidef = sparkSession.read().format(csvFormat).option("delimiter", ",")
                .schema(getStringTypeSchema(tlbcidefColumns)).csv(Paths.get(stepInputDir, cicliNdgPath).toString());
        // 49

        // cicli_ndg_princ = FILTER tlbcidef BY cd_collegamento IS NULL;
        // cicli_ndg_coll = FILTER tlbcidef BY cd_collegamento IS NOT NULL;
        Dataset<Row> cicliNdgPrinc = tlbcidef.filter(tlbcidef.col("cd_collegamento").isNull());
        Dataset<Row> cicliNdgColl = tlbcidef.filter(tlbcidef.col("cd_collegamento").isNotNull());

        // 59
        List<String> tlbgaranColumns = Arrays.asList("cd_istituto", "ndg", "sportello", "dt_riferimento", "conto_esteso",
                "cd_puma2", "ide_garanzia", "importo", "fair_value");

        Dataset<Row> tlbgaran = sparkSession.read().format(csvFormat).option("delimiter", ",")
                .schema(getStringTypeSchema(tlbgaranColumns)).csv(Paths.get(stepInputDir, tlbgaranPath).toString());

        // 71

        // JOIN  tlbgaran BY (cd_istituto, ndg), cicli_ndg_princ BY (codicebanca_collegato, ndg_collegato);
        Column joinCondition = tlbgaran.col("cd_istituto").equalTo(cicliNdgPrinc.col("codicebanca_collegato"))
                .and(tlbgaran.col("ndg").equalTo(cicliNdgPrinc.col("ndg_collegato")));

        // ToDate( (chararray)dt_riferimento,'yyyyMMdd') >= ToDate( (chararray)datainiziodef,'yyyyMMdd' )
        Column dtRiferimentoDataInizioDefFilterCol = tlbgaran.col("dt_riferimento").geq(tlbcidef.col("datainiziodef"));

        /* and SUBSTRING( (chararray)dt_riferimento,0,6 ) <=
            SUBSTRING( (chararray)LeastDate( (int)ToString(SubtractDuration(ToDate((chararray)datafinedef,'yyyyMMdd' ),'P1M'),'yyyyMMdd') ,
                        $data_a), 0,6 );
        */

        // we need to format $data_a from yyyy-MM-dd to yyyyMMdd
        Column dataACol = functions.lit(changeDateFormat(dataA, "yyyy-MM-dd", "yyyyMMdd"));
        Column dataFineDefDataALeastDateCol = leastDate(subtractDuration(tlbcidef.col("datafinedef"), "yyyyMMdd", 1),
                dataACol, "yyyMMdd");

        Column dtRiferimentoLeastDateFilterCol = substringAndCastToInt(tlbgaran.col("dt_riferimento"), 0, 6)
                .leq(substringAndCastToInt(dataFineDefDataALeastDateCol, 0, 6));

        /*
          tlbgaran::cd_istituto			 AS cd_isti
         ,tlbgaran::ndg					 AS ndg
         ,tlbgaran::sportello			 AS sportello
         ,tlbgaran::dt_riferimento		 AS dt_riferimento
         ,tlbgaran::conto_esteso		 AS conto_esteso
         ,tlbgaran::cd_puma2			 AS cd_puma2
         ,tlbgaran::ide_garanzia		 AS ide_garanzia
         ,tlbgaran::importo				 AS importo
         ,tlbgaran::fair_value			 AS fair_value
         ,cicli_ndg_princ::codicebanca	 AS codicebanca
         ,cicli_ndg_princ::ndgprincipale AS ndgprincipale
         ,cicli_ndg_princ::datainiziodef AS	datainiziodef
         */
        List<Column> selectColsList = new ArrayList<>(Collections.singletonList(tlbgaran.col("cd_istituto").alias("cd_isti")));

        List<String> tlbgaranSelectColNames = Arrays.asList("ndg", "sportello", "dt_riferimento", "conto_esteso",
                "cd_puma2", "ide_garanzia", "importo", "fair_value");
        List<String> cicliNdgSelectColNames = Arrays.asList("codicebanca", "ndgprincipale", "datainiziodef");
        selectColsList.addAll(selectDfColumns(tlbgaran, tlbgaranSelectColNames));
        selectColsList.addAll(selectDfColumns(cicliNdgPrinc, cicliNdgSelectColNames));

        Seq<Column> selectColsSeq = toScalaColSeq(selectColsList);

        Dataset<Row> tlbcidefTlbgaranPrinc = tlbgaran.join(cicliNdgPrinc, joinCondition, "inner").filter(
                dtRiferimentoDataInizioDefFilterCol.and(dtRiferimentoLeastDateFilterCol)).select(selectColsSeq);

        // JOIN  tlbgaran BY (cd_istituto, ndg, dt_riferimento), cicli_ndg_coll BY (codicebanca_collegato, ndg_collegato, dt_rif_udct);
        joinCondition = tlbgaran.col("cd_istituto").equalTo(cicliNdgColl.col("codicebanca_collegato"))
                .and(tlbgaran.col("ndg").equalTo(cicliNdgColl.col("ndg_collegato")))
                .and(tlbgaran.col("dt_riferimento").equalTo(cicliNdgColl.col("dt_rif_udct")));

        selectColsList = new ArrayList<>(Collections.singletonList(tlbgaran.col("cd_istituto").alias("cd_isti")));
        selectColsList.addAll(selectDfColumns(tlbgaran, tlbgaranSelectColNames));
        selectColsList.addAll(selectDfColumns(cicliNdgColl, cicliNdgSelectColNames));
        selectColsSeq = toScalaColSeq(selectColsList);

        Dataset<Row> tlbcidefTlbgaranColl = tlbgaran.join(cicliNdgColl, joinCondition, "inner").select(selectColsSeq);

        Dataset<Row> frappPumaOut = tlbcidefTlbgaranPrinc.union(tlbcidefTlbgaranColl).distinct();

        String frappPumaOutPath = getPropertyValue("frapp.puma.out");
        logger.debug("frappPumaOutPath: " + frappPumaOutPath);

        frappPumaOut.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite)
                .csv(Paths.get(stepOutputDir, frappPumaOutPath).toString());
    }
}
