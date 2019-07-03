package steps;

import org.apache.commons.cli.*;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import scala.collection.JavaConverters;
import scala.collection.Seq;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class FrappNdgMonthly extends AbstractStep{

    // required parameters
    private String dataA;
    private int numeroMesi1;
    private int numeroMesi2;

    FrappNdgMonthly(String[] args){

        // define option dataA, periodo, numeroMesi1, numeroMesi2
        Option dataAOption = new Option("da", "dataA", true, "parametro dataA");
        Option numeroMesi1Option = new Option("nm1", "numero-mesi-1", true, "parametro numero-mesi-1");
        Option numeroMesi2Option = new Option("nm2", "numero-mesi-2", true, "parametro numero-mesi-2");

        // et them as required
        dataAOption.setRequired(true);
        numeroMesi1Option.setRequired(true);
        numeroMesi2Option.setRequired(true);

        // add them to Options
        Options options = new Options();
        options.addOption(dataAOption);
        options.addOption(numeroMesi1Option);
        options.addOption(numeroMesi2Option);


        CommandLineParser commandLineParser = new BasicParser();

        // try to parse and retrieve command line arguments
        try{

            CommandLine cmd = commandLineParser.parse(options, args);
            dataA = cmd.getOptionValue("dataA");
            numeroMesi1 = Integer.parseInt(cmd.getOptionValue("numero-mesi-1"));
            numeroMesi2 = Integer.parseInt(cmd.getOptionValue("numero-mesi-2"));

            logger.info("dataA: " + dataA);
            logger.info("numeroMesi1: " + numeroMesi1);
            logger.info("numeroMesi2: " + numeroMesi2);

        }
        catch (ParseException e) {

            logger.info("ParseException: " + e.getMessage());
            dataA = "2018-12-01";
            numeroMesi1 = 1;
            numeroMesi2 = 2;

            logger.info("Setting dataA to: " + dataA);
            logger.info("Setting numeroMesi1 to:" + numeroMesi1);
            logger.info("Setting numeroMesi2 to: " + numeroMesi2);
        }

    }

    public void run() {

        String csvFormat = getProperty("csv_format");
        String frappNdgMonthlyInputDir = getProperty("FRAPP_NDG_MONTHLY_INPUT_DIR");
        String cicliNdgPathCsv = getProperty("CICLI_NDG_PATH_CSV");
        logger.info("csvFormat: " + csvFormat);
        logger.info("frappNdgMonthlyInputDir: " + frappNdgMonthlyInputDir);
        logger.info("cicliNdgPathCsv: " + cicliNdgPathCsv);

        // 26
        List<String> tlbcidefColumns = Arrays.asList("codicebanca", "ndgprincipale", "datainiziodef", "datafinedef", "datainiziopd",
                "datainizioristrutt", "datainizioinc", "datainiziosoff", "c_key", "tipo_segmne", "sae_segm", "rae_segm", "segmento",
                "tp_ndg", "provincia_segm", "databilseg", "strbilseg", "attivobilseg", "fatturbilseg", "ndg_collegato", "codicebanca_collegato",
                "cd_collegamento", "cd_fiscale", "dt_rif_udct");

        StructType tlbcidefSchema = setDfSchema(tlbcidefColumns);
        String cicliNdgPathCsvPath = Paths.get(frappNdgMonthlyInputDir, cicliNdgPathCsv).toString();
        logger.info("cicliNdgPathCsvPath: " + cicliNdgPathCsvPath);

        Dataset<Row> tlbcidef = sparkSession.read().format(csvFormat).option("delimiter", ",").schema(tlbcidefSchema).csv(cicliNdgPathCsvPath);
        // 53

        // 58
        Dataset<Row> cicliNdgPrinc = tlbcidef.filter(tlbcidef.col("cd_collegamento").isNull());
        Dataset<Row> cicliNdgColl = tlbcidef.filter(tlbcidef.col("cd_collegamento").isNotNull());
        // 60

        // 69
        List<String> tlburttColumns = Arrays.asList("cd_istituto", "ndg", "sportello", "conto", "progr_segmento", "dt_riferimento", "conto_esteso",
                "forma_tecnica", "flag_durata_contr", "cred_agevolato", "operazione_pool", "dt_accensione", "dt_estinzione", "dt_scadenza",
                "organo_deliber", "dt_delibera", "dt_scad_fido", "origine_rapporto", "tp_ammortamento", "tp_rapporto", "period_liquid",
                "freq_remargining", "cd_prodotto_ris", "durata_originaria", "divisa", "score_erogaz", "durata_residua", "categoria_sof",
                "categoria_inc", "dur_res_default", "flag_margine", "dt_entrata_def", "tp_contr_rapp", "cd_eplus", "r792_tipocartol");

        StructType tlburttSchema = setDfSchema(tlburttColumns);
        String tlburttCsv = getProperty("TLBURTT_CSV");
        String tlburttCsvPath = Paths.get(frappNdgMonthlyInputDir, tlburttCsv).toString();
        logger.info("tlburttCsv: " + tlburttCsv);
        logger.info("tlburttCsvPath: " + tlburttCsvPath);

        Dataset<Row> tlburtt = sparkSession.read().format(csvFormat).option("delimiter", ",").schema(tlburttSchema).csv(tlburttCsvPath);
        // 107

        // 111
        Dataset<Row> tlburttFilter = tlburtt.filter(castCol(tlburtt, "progr_segmento", DataTypes.IntegerType).equalTo(0));

        // ToDate((chararray)dt_riferimento,'yyyyMMdd') >= SubtractDuration(ToDate((chararray)datainiziodef,'yyyyMMdd'),'$numero_mesi_1')
        Column dtRiferimentoFilterCol = getUnixTimeStampCol(tlburttFilter.col("dt_riferimento"), "yyyyMMdd").$greater$eq(
                getUnixTimeStampCol(functions.add_months(cicliNdgPrinc.col("datainiziodef"), -numeroMesi1), "yyyyMMdd"));

        Column dataFineDefCol = functions.substring(functions.add_months(leastDate(functions.add_months(cicliNdgPrinc.col("datafinedef"), -1),
                functions.date_format(functions.lit(dataA), "yyyyMMdd"), "yyyyMMdd"), numeroMesi2), 0, 6);

        Column dataFineDefFilterCol = getUnixTimeStampCol(tlburttFilter.col("dt_riferimento"), "yyyyMMdd").$less$eq(
                getUnixTimeStampCol(dataFineDefCol, "yyyyMMdd"));

        // list of columns to be selected on cicliNdgPrinc
        List<String> cicliNdgPrincSelectColNames = Arrays.asList("codicebanca", "cicli_ndg_princ",  "ndgprincipale", "codicebanca_collegato",
                "ndg_collegato", "datainiziodef", "datafinedef");
        List<Column> tlbcidefUrttPrincCols = selectDfColumns(cicliNdgPrinc, cicliNdgPrincSelectColNames);

        // list of columns to be selected on tlburttFilter
        List<String> tlburttFilterSelectColNames = Arrays.asList("cd_istituto", "ndg", "sportello", "conto", "dt_riferimento", "conto_esteso",
                "forma_tecnica", "dt_accensione", "dt_estinzione", "dt_scadenza", "tp_ammortamento", "tp_rapporto", "period_liquid", "cd_prodotto_ris",
                "durata_originaria", "divisa", "durata_residua", "tp_contr_rapp");
        List<Column> tlburttFilterSelectCols = selectDfColumns(tlburttFilter, tlburttFilterSelectColNames);
        tlbcidefUrttPrincCols.addAll(tlburttFilterSelectCols);

        // conversion to scala Seq
        Seq<Column> tlbcidefUrttPrincColSeq = JavaConverters.asScalaIteratorConverter(tlbcidefUrttPrincCols.iterator()).asScala().toSeq();
        Dataset<Row> tlbcidefUrttPrinc = cicliNdgPrinc.join(tlburttFilter, cicliNdgPrinc.col("codicebanca_collegato").equalTo(
                tlburttFilter.col("cd_istituto")).and(cicliNdgPrinc.col("ndg_collegato").equalTo(tlburttFilter.col("ndg"))))
                .filter(dtRiferimentoFilterCol.and(dataFineDefFilterCol))
                .select(tlbcidefUrttPrincColSeq);
        // 158

        // as tlbcidefUrttColl follows the same pipeline of tlbcidefUrttPrinc, except for the fact that
        // we tlbcidefUrttColl uses cicliNdgColl as opposed to tlbcidefUrttPrinc that uses cicliNdgPrinc,
        // we simplt slightly modify the previously defined column conditions and select columns

        dtRiferimentoFilterCol = getUnixTimeStampCol(tlburttFilter.col("dt_riferimento"), "yyyyMMdd").$greater$eq(
                getUnixTimeStampCol(functions.add_months(cicliNdgColl.col("datainiziodef"), -numeroMesi1), "yyyyMMdd"));

        dataFineDefCol = functions.substring(functions.add_months(leastDate(functions.add_months(cicliNdgColl.col("datafinedef"), -1),
                functions.date_format(functions.lit(dataA), "yyyyMMdd"), "yyyyMMdd"), numeroMesi2), 0, 6);

        dataFineDefFilterCol = getUnixTimeStampCol(tlburttFilter.col("dt_riferimento"), "yyyyMMdd").$less$eq(
                getUnixTimeStampCol(dataFineDefCol, "yyyyMMdd"));

        List<Column> tlbcidefUrttCollCols = selectDfColumns(cicliNdgColl, cicliNdgPrincSelectColNames);
        tlbcidefUrttCollCols.addAll(tlburttFilterSelectCols);

        // conversion to scala Seq
        Seq<Column> tlbcidefUrttCollColsSeq = JavaConverters.asScalaIteratorConverter(tlbcidefUrttCollCols.iterator()).asScala().toSeq();
        Dataset<Row> tlbcidefUrttColl = cicliNdgColl.join(tlburttFilter, cicliNdgColl.col("codicebanca_collegato").equalTo(
                tlburttFilter.col("cd_istituto")).and(cicliNdgColl.col("ndg_collegato").equalTo(tlburttFilter.col("ndg"))))
                .filter(dtRiferimentoFilterCol.and(dataFineDefFilterCol))
                .select(tlbcidefUrttCollColsSeq);

        Dataset<Row> tlbcidefTlburtt = tlbcidefUrttPrinc.union(tlbcidefUrttColl).distinct();

        String frappNdgMonthlyOutputDir = getProperty("FRAPP_NDG_MONTHLY_OUTPUT_DIR");
        String tlbcidefTlburttCsv = getProperty("TLBCIDEF_TLBURTT");
        logger.info("frappNdgMonthlyOutputDir: " + frappNdgMonthlyOutputDir);
        logger.info("tlbcidefTlburttCsv: " + tlbcidefTlburttCsv);

        tlbcidefTlburtt.write().format(csvFormat).option("delimiter", ",").mode(SaveMode.Overwrite).csv(Paths.get(
                frappNdgMonthlyOutputDir, tlbcidefTlburttCsv).toString());
    }
}