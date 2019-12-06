package steps.lgdstep;

import org.apache.log4j.Logger;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.functions;
import steps.abstractstep.AbstractStep;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class QuadFanag extends AbstractStep {

    private String ufficio;

    public QuadFanag(String loggerName, String ufficio){

        super(loggerName);
        logger = Logger.getLogger(loggerName);

        this.ufficio = ufficio;

        stepInputDir = getLGDPropertyValue("quad.fanag.input.dir");
        stepOutputDir = getLGDPropertyValue("quad.fanag.output.dir");

        logger.debug("stepInputDir: " + stepInputDir);
        logger.debug("stepOutputDir: " + stepOutputDir);
    }

    @Override
    public void run() {

        String csvFormat = getLGDPropertyValue("csv.format");
        String hadoopFanagCsv = getLGDPropertyValue("hadoop.fanag.csv");
        String oldFanagLoadCsv = getLGDPropertyValue("old.fanag.load.csv");
        String fcollCsv = getLGDPropertyValue("fcoll.csv");
        String tlbucolLoadCsv = getLGDPropertyValue("tlbucol.load.csv");

        logger.debug("csvFormat: " + csvFormat);
        logger.debug("hadoopFanagCsv: " + hadoopFanagCsv);
        logger.debug("oldFanagLoadCsv: " + oldFanagLoadCsv);
        logger.debug("fcollCsv: " + fcollCsv);
        logger.debug("tlbucolLoadCsv: " + tlbucolLoadCsv);

        List<String> hadoopFanagColNames = Arrays.asList("codicebanca", "ndg", "datariferimento", "totaccordato", "totutilizzo",
                "totaccomortgage", "totutilmortgage", "totsaldi0063", "totsaldi0260", "naturagiuridica", "intestazione", "codicefiscale",
                "partitaiva", "sae", "rae", "ciae", "provincia", "provincia_cod", "sportello", "attrn011", "attrn175", "attrn186", "cdrapristr",
                "segmento", "cd_collegamento", "ndg_caponucleo", "flag_ristrutt", "codicebanca_princ", "ndgprincipale", "datainiziodef");

        Dataset<Row> hadoopFanag = sparkSession.read().format(csvFormat)
                .schema(getStringTypeSchema(hadoopFanagColNames))
                .csv(Paths.get(stepInputDir, hadoopFanagCsv).toString());

        List<String> oldFanagLoadColNames = Arrays.asList("CODICEBANCA", "NDG", "DATARIFERIMENTO", "TOTACCORDATO", "TOTUTILIZZO",
                "TOTACCOMORTGAGE", "TOTUTILMORTGAGE", "TOTSALDI0063", "TOTSALDI0260", "NATURAGIURIDICA", "INTESTAZIONE", "CODICEFISCALE",
                "PARTITAIVA", "SAE", "RAE", "CIAE", "PROVINCIA_COD", "PROVINCIA", "SPORTELLO", "ATTRN011", "ATTRN175", "ATTRN186", "CDRAPRISTR",
                "SEGMENTO");

        Dataset<Row> oldFanagLoad = sparkSession.read().format(csvFormat)
                .schema(getStringTypeSchema(oldFanagLoadColNames))
                .csv(Paths.get(stepInputDir, oldFanagLoadCsv).toString());

        List<String> fcollColNames = Arrays.asList("CODICEBANCA", "NDGPRINCIPALE", "DATAINIZIODEF", "DATAFINEDEF", "DATA_DEFAULT",
                "ISTITUTO_COLLEGATO", "NDG_COLLEGATO", "DATA_COLLEGAMENTO", "CUMULO");

        Dataset<Row> fcoll = sparkSession.read().format(csvFormat)
                .schema(getStringTypeSchema(fcollColNames))
                .csv(Paths.get(stepInputDir, fcollCsv).toString());

        /*
        JOIN oldfanag_load BY (CODICEBANCA, NDG, DATARIFERIMENTO),
        fcoll BY (ISTITUTO_COLLEGATO, NDG_COLLEGATO, DATA_COLLEGAMENTO);
         */

        Column oldFanagJoinCondition = oldFanagLoad.col("CODICEBANCA").equalTo(fcoll.col("ISTITUTO_COLLEGATO"))
                .and(oldFanagLoad.col("NDG").equalTo(fcoll.col("NDG_COLLEGATO")))
                .and(oldFanagLoad.col("DATARIFERIMENTO").equalTo(fcoll.col("DATA_COLLEGAMENTO")));

        Dataset<Row> oldFanag = oldFanagLoad.join(fcoll, oldFanagJoinCondition).select(oldFanagLoad.col("*"),
                fcoll.col("CODICEBANCA").alias("CODICEBANCA_PRINC"), fcoll.col("NDGPRINCIPALE"),
                fcoll.col("DATAINIZIODEF"));

        // JOIN hadoop_fanag BY (codicebanca_princ, ndgprincipale, datainiziodef, codicebanca, ndg, datariferimento) FULL OUTER
        // oldfanag BY (CODICEBANCA_PRINC, NDGPRINCIPALE, DATAINIZIODEF, CODICEBANCA, NDG, DATARIFERIMENTO );
        // FILTER hadoop_fanag_oldfanag_join BY oldfanag::CODICEBANCA IS NULL;

        Column soloHadoopFanagFilterJoinCondition =
                getQuadJoinCondition(hadoopFanag, oldFanag,
                        Arrays.asList("codicebanca_princ", "ndgprincipale", "datainiziodef", "codicebanca", "ndg", "datariferimento"));

        // FILTER hadoop_fanag_oldfanag_join BY hadoop_fanag::codicebanca IS NULL;
        Dataset<Row> soloHadoopFanagFilter = hadoopFanag.join(oldFanag, soloHadoopFanagFilterJoinCondition, "full_outer")
                .filter(oldFanag.col("CODICEBANCA").isNull());

        Dataset<Row> soloOldFanagFilter = hadoopFanag.join(oldFanag, soloHadoopFanagFilterJoinCondition, "full_outer")
                .filter(hadoopFanag.col("codicebanca").isNull());

        // JOIN hadoop_fanag BY (codicebanca, ndg, datariferimento) FULL OUTER,
        // oldfanag BY (CODICEBANCA, NDG, DATARIFERIMENTO );
        // FILTER hadoop_fanag_oldfanag_join_ndg BY oldfanag::CODICEBANCA IS NULL;

        Column soloHadoopFanagFilterNdgJoinCondition =
                getQuadJoinCondition(hadoopFanag, oldFanag,
                        Arrays.asList("codicebanca", "ndg", "datariferimento"));

        Dataset<Row> soloHadoopFanagFilterNdg = hadoopFanag.join(oldFanag, soloHadoopFanagFilterNdgJoinCondition, "full_outer")
                .filter(oldFanag.col("CODICEBANCA").isNull());

        // FILTER hadoop_fanag_oldfanag_join BY hadoop_fanag::codicebanca IS NULL;
        Dataset<Row> soloOldFanagFilterNdg = hadoopFanag.join(oldFanag, soloHadoopFanagFilterNdgJoinCondition, "full_outer")
                .filter(hadoopFanag.col("codicebanca").isNull());

        List<String> tlbucolLoadColNames = Arrays.asList("cd_istituto", "ndg", "cd_collegamento", "ndg_collegato", "dt_riferimento");
        Dataset<Row> tlbucolLoad = sparkSession.read().format(csvFormat)
                .schema(getStringTypeSchema(tlbucolLoadColNames))
                .csv(Paths.get(stepInputDir, tlbucolLoadCsv).toString());

        // FILTER tlbucol_load BY cd_collegamento=='103'
        //                     OR cd_collegamento=='105'
        //                     OR cd_collegamento=='106'
        //                     OR cd_collegamento=='107'
        //                     OR cd_collegamento=='207';

        Dataset<Row> tlbucolFiltered = tlbucolLoad.filter(tlbucolLoad.col("cd_collegamento").isin("103", "105", "106", "107", "207"));

        Dataset<Row> hadoopFanagOut = soloHadoopFanagFilter.select(functions.lit(ufficio).alias("ufficio"), soloHadoopFanagFilter.col("*"));
        Dataset<Row> hadoopFanagOutNdg = soloHadoopFanagFilterNdg.select(functions.lit(ufficio).alias("ufficio"), soloOldFanagFilterNdg.col("*"));

        // JOIN solo_oldfanag_filter BY (CODICEBANCA, NDG, DATARIFERIMENTO),
        // tlbucol_filtered BY (cd_istituto, ndg_collegato, dt_riferimento);
        Column oldFanagOutCollJoinCondition = soloOldFanagFilter.col("CODICEBANCA").equalTo(tlbucolFiltered.col("cd_istituto"))
                .and(soloOldFanagFilter.col("NDG").equalTo(tlbucolFiltered.col("ndg_collegato")))
                .and(soloOldFanagFilter.col("DATARIFERIMENTO").equalTo(tlbucolFiltered.col("dt_riferimento")));

        Dataset<Row> oldFanagOutColl = soloOldFanagFilter.join(tlbucolFiltered, oldFanagOutCollJoinCondition).select(
                functions.lit(ufficio).alias("ufficio"), soloHadoopFanagFilter.col("*"));

        // JOIN solo_oldfanag_filter BY (CODICEBANCA, NDG, DATARIFERIMENTO)
        // ,tlbucol_filtered BY (cd_istituto, ndg, dt_riferimento);
        Column oldFanagPrincJoinCondition = soloOldFanagFilter.col("CODICEBANCA").equalTo(tlbucolFiltered.col("cd_istituto"))
                .and(soloOldFanagFilter.col("NDG").equalTo(tlbucolFiltered.col("ndg")))
                .and(soloOldFanagFilter.col("DATARIFERIMENTO").equalTo(tlbucolFiltered.col("dt_riferimento")));

        Dataset<Row> oldFanagOutPrinc = soloOldFanagFilter.join(tlbucolFiltered, oldFanagPrincJoinCondition)
                .select(functions.lit(ufficio).alias("ufficio"), soloOldFanagFilter.col("*"));

        // oldfanag_out = UNION oldfanag_out_coll ,oldfanag_out_princ
        Dataset<Row> oldFanagOut = oldFanagOutColl.union(oldFanagOutPrinc);

        // JOIN solo_oldfanag_filter_ndg BY (CODICEBANCA, NDG, DATARIFERIMENTO),
        // tlbucol_filtered BY (cd_istituto, ndg_collegato, dt_riferimento);
        Column oldFanagOutCollNdgJoinCondition = soloOldFanagFilterNdg.col("CODICEBANCA").equalTo(tlbucolFiltered.col("cd_istituto"))
                .and(soloOldFanagFilterNdg.col("NDG").equalTo(tlbucolFiltered.col("ndg_collegato")))
                .and(soloOldFanagFilterNdg.col("DATARIFERIMENTO").equalTo(tlbucolFiltered.col("dt_riferimento")));

        Dataset<Row> oldFanagOutCollNdg = soloOldFanagFilterNdg.join(tlbucolFiltered, oldFanagOutCollNdgJoinCondition)
                .select(functions.lit(ufficio).alias("ufficio"), soloOldFanagFilterNdg.col("*"));

        // JOIN solo_oldfanag_filter_ndg BY (CODICEBANCA, NDG, DATARIFERIMENTO),
        // tlbucol_filtered BY (cd_istituto, ndg, dt_riferimento);
        Column oldFanagOutPrincNdgJoinCondition = soloOldFanagFilterNdg.col("CODICEBANCA").equalTo(tlbucolFiltered.col("cd_istituto"))
                .and(soloOldFanagFilterNdg.col("NDG").equalTo(tlbucolFiltered.col("ndg")))
                .and(soloOldFanagFilterNdg.col("DATARIFERIMENTO").equalTo(tlbucolFiltered.col("dt_riferimento")));

        Dataset<Row> oldFanagOutPrincNdg = soloOldFanagFilterNdg.join(tlbucolFiltered, oldFanagOutPrincNdgJoinCondition)
                .select(functions.lit(ufficio).alias("ufficio"), soloOldFanagFilterNdg.col("*"));

        // oldfanag_out_ndg = UNION oldfanag_out_coll_ndg, oldfanag_out_princ_ndg
        Dataset<Row> oldFanagOutNdg = oldFanagOutCollNdg.union(oldFanagOutPrincNdg);

        String hadoopFanagOutPath = getLGDPropertyValue("hadoop.fanag.out");
        String oldFanagOutPath = getLGDPropertyValue("old.fanag.out");
        String hadoopFanagOutNdgPath = getLGDPropertyValue("hadoop.fanag.out.ndg");
        String oldFanagOutNdgPath = getLGDPropertyValue("old.fanag.out.ndg");

        logger.debug("hadoopFanagOutPath: " + hadoopFanagOutPath);
        logger.debug("oldFanagOutPath: " + oldFanagOutPath);
        logger.debug("hadoopFanagOutNdgPath: " + hadoopFanagOutNdgPath);
        logger.debug("oldFanagOutNdgPath: " + oldFanagOutNdgPath);

        hadoopFanagOut.write().format(csvFormat).mode(SaveMode.Overwrite).csv(Paths.get(stepOutputDir, hadoopFanagOutPath).toString());

        oldFanagOut.write().format(csvFormat).mode(SaveMode.Overwrite).csv(Paths.get(stepOutputDir, oldFanagOutPath).toString());

        hadoopFanagOutNdg.write().format(csvFormat).mode(SaveMode.Overwrite).csv(Paths.get(stepOutputDir, hadoopFanagOutNdgPath).toString());

        oldFanagOutNdg.write().format(csvFormat).mode(SaveMode.Overwrite).csv(Paths.get(stepOutputDir, oldFanagOutNdgPath).toString());

    }
}
