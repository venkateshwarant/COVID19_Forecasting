import java.io._
import org.apache.spark.rdd._
import org.apache.spark.mllib.stat.Statistics
import org.apache.spark.ml.feature.{VectorAssembler, VectorIndexer}
import org.apache.spark.ml.regression.{RandomForestRegressionModel, RandomForestRegressor}
import org.apache.spark.ml.{PipelineModel, Pipeline}
import org.apache.spark.ml.tuning.{ParamGridBuilder, CrossValidator}
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.apache.spark.ml.regression.LinearRegression
import org.sameersingh.scalaplot._
import org.sameersingh.scalaplot.gnuplot.GnuplotPlotter
import org.sameersingh.scalaplot.jfreegraph.JFGraphPlotter
import org.sameersingh.scalaplot.Implicits._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.{array, col, explode, lit, struct}
import org.apache.spark.ml.feature.PolynomialExpansion
/*
* This method is used to merge two sets of column
*/
def expr(myCols: Set[String], allCols: Set[String]) = {
   allCols.toList.map(x => x match {
      case x if myCols.contains(x) => col(x)
      case _ => lit(null).as(x)
   })
  }
/*
* Returns the list of files in the given directory
*/
def getListOfFiles(dir: String):List[File] = {
   val d = new File(dir)
   if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
   } else {
       List[File]()
   }
}
/*
* This method renames the columns of the input dataframe to a common format.
* This is done because the columns of the same data on different time frame has different column names.
* For eg. column named "Province/State" is called "Province_State" at another timeframe.
*/
def renameColumns(df:org.apache.spark.sql.DataFrame):org.apache.spark.sql.DataFrame = {
  return df.withColumnRenamed("Province/State","Province_State").
    withColumnRenamed("Country/Region","Country_Region").
    withColumnRenamed("Last Update","Last_Update").
    withColumnRenamed("Lat","Latitude").
    withColumnRenamed("Long_","Longitude")
}
/*
* Returns spearman correlation of two input RDDs
*/
def getCorrelation( a:RDD[Double], b:RDD[Double] ):Double = {
      val correlation: Double = Statistics.corr(a, b, "spearman")
       return correlation
}
var formats=Seq("MM/dd/yy HH:mm","MM/dd/yyyy HH:mm", "yyyy-MM-ddTHH:mm:ss", "yyyy-MM-dd HH:mm:ss")
def to_timestamp_simple(col: org.apache.spark.sql.Column, formats: Seq[String]): org.apache.spark.sql.Column = {
   coalesce(formats.map(fmt => to_timestamp(col, fmt)): _*)
}
val initialFile= "07-30-2020.csv"
var outputDF= spark.read.format("csv").option("header", "true").option("mode", "DROPMALFORMED").option("inferSchema", true).csv("/home/users/vthamilselvan/venkat/COVID-19/csse_covid_19_data/csse_covid_19_daily_reports/"+initialFile)
outputDF = renameColumns(outputDF)
val files= getListOfFiles("/home/users/vthamilselvan/venkat/COVID-19/csse_covid_19_data/csse_covid_19_daily_reports/")
for (file <- files){
  if(file.getName() != initialFile && file.getName().contains(".csv")){
   print("\n"+file.getName()+" Started           ") // This loop is beacaue the same column has different names in different dates such as Country/Region and Country_Region
   val df = spark.read.format("csv").option("header", "true").option("mode", "DROPMALFORMED").option("inferSchema", true).csv(file.getPath())
   val df_new = renameColumns(df)
   val cols = df_new.columns.toSet
   val cols1 = outputDF.columns.toSet
   val total = cols ++ cols1
   val newOutput= df_new.select(expr(cols, total):_*).unionAll(outputDF.select(expr(cols1, total):_*))
   outputDF= newOutput
   print(file.getName()+" Ended")
 }
}
var outputDF1= outputDF.withColumn("Last_Update",  to_timestamp_simple(col("Last_Update"),formats)).
  withColumn("Lat_Lon",concat(col("Latitude"),lit(","), col("Longitude"))).
  withColumn("Recovered", when($"Recovered".isNull, 0).otherwise($"Recovered")).
  withColumn("Deaths", when($"Deaths".isNull, 0).otherwise($"Deaths")).
  withColumn("Confirmed", when($"Confirmed".isNull, 0).otherwise($"Confirmed")).
  withColumn("Active", when($"Active".isNull, $"Confirmed"-$"Deaths"-$"Recovered").otherwise($"Active"))
outputDF1.cache()
/* 1. a. Rank the Country/Region with the most number of Deaths
*     Death count is accumulated. So, for a region, the most recent update has the whole death count for that region.
*/
var recent = outputDF1.groupBy("Country_Region").agg(max("Last_Update") as "Recent_Update").select(col("Recent_Update"))
var recentDateString = recent.first.get(0).toString
val deathList= outputDF1.filter(col("Last_Update")===recentDateString).
  groupBy(col("Country_Region")).
 agg(min(col("Deaths")) as "Min_Deaths",max(col("Deaths")) as "Max_Deaths",sum(col("Deaths")) as "Sum_Deaths")
deathList.sort(desc("Sum_Deaths")).show(20, false) //Top 20 rank list
deathList.agg(sum(col("Sum_Deaths"))).show //The total deaths occcured worldwide
/* 1.b. Rank the Country/Region with the most number of confirmed cases
*     Confirmed case count is accumulated. So, for a region, the most recent update has the whole confirmed case count for that region.
*/
val confirmedList= outputDF1.filter(col("Last_Update")===recentDateString).
  groupBy(col("Country_Region")).agg(min(col("Confirmed")) as "Min_Confirmed",max(col("Confirmed")) as "Max_Confirmed",sum(col("Confirmed")) as "Sum_Confirmed")
confirmedList.sort(desc("Sum_Confirmed")).show(20, false) //Top 20 rank list
confirmedList.agg(sum(col("Sum_Confirmed"))).show //The total confirmed cases worldwide
/* 1.c. Rank the Country/Region with the most number of active cases
*     Active case count is accumulated. So, for a region, the most recent update has the whole active case count for that region.
*/
val activeList= outputDF1.filter(col("Last_Update")===recentDateString).
  groupBy(col("Country_Region")).agg(min(col("Active")) as "Min_Active",max(col("Active")) as "Max_Active",sum(col("Active")) as "Sum_Active")
activeList.sort(desc("Sum_Active")).show(20, false) //Top 20 rank list
activeList.agg(sum(col("Sum_Active"))).show //The total active cases worldwide
/* 1.d. Rank the Country/Region with the incidence
*     incidence rate is accumulated. So, for a region, the most recent update has the updated incidence rate for that region.
*/
val incidenceList= outputDF1.filter(col("Last_Update")===recentDateString).groupBy(col("Country_Region")).
  agg(avg(col("Incidence_Rate")) as "Avg_Incidence_Rate")
incidenceList.sort(desc("Avg_Incidence_Rate")).show(20, false) //Top 20 rank list
incidenceList.agg(avg(col("Avg_Incidence_Rate"))).show //Glbal average of incidence rate
/* 1.e. Rank the Country/Region with the case-fatality ratio
*     Case-Fatality ratio is accumulated. So, for a region, the most recent update has the updated Case-Fatality ratio for that region.
*/
val confirmedDF = confirmedList.select("Country_Region","Sum_Confirmed")
val deathDF = deathList.select("Country_Region","Sum_Deaths")
val combinedDF= confirmedDF.join(deathDF, Seq("Country_Region"))
val cfrList = combinedDF.withColumn("CFR", lit(100)*col("Sum_Deaths")/col("Sum_Confirmed"))
cfrList.sort(desc("CFR")).show(20, false) //Top 20 rank list
cfrList.filter(col("Country_Region")==="Luxembourg").show //Case-Fatality ratio of Luxembourg
cfrList.select(avg($"CFR")).show() //Global average CFR

/* 1.f. Rank the Country/Region with the recovered cases
*     recovered cases are accumulated. So, for a region, the most recent update has the updated recovered cases for that region.
*/
val recoveredList= outputDF1.filter(col("Last_Update")===recentDateString).groupBy(col("Country_Region")).
 agg(min(col("Recovered")) as "Min_Recovered",max(col("Recovered")) as "Max_Recovered",sum(col("Recovered")) as "Sum_Recovered")
recoveredList.sort(desc("Sum_Recovered")).show(20, false) //Top 20 rank list
recoveredList.agg(sum(col("Sum_Recovered"))).show //The total active cases worldwide
/* 2. Correlation between Confirmed, Death and Recovered case counts
*/
//For Luxembourg
val luxDF= outputDF1.filter(col("Country_Region")==="Luxembourg").
  selectExpr("date(Last_Update) AS Last_Update", "Confirmed", "Recovered", "Deaths").
  groupBy("Last_Update").agg(sum("Confirmed") as "Confirmed", sum("Recovered") as "Recovered", sum("Deaths") as "Deaths").sort(desc("Last_Update"))
val luxConfirmedRDD = luxDF.select("Confirmed").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val luxRecoveredRDD = luxDF.select("Recovered").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val luxDeathRDD = luxDF.select("Deaths").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
var confirmed_recovered_correlation = getCorrelation(luxConfirmedRDD,luxRecoveredRDD)
var confirmed_death_correlation = getCorrelation(luxConfirmedRDD,luxDeathRDD)
var death_recovered_correlation = getCorrelation(luxDeathRDD,luxRecoveredRDD)
println(s"Confirmed-Recovered correlation is: $confirmed_recovered_correlation")
println(s"Confirmed-Death correlation is: $confirmed_death_correlation")
println(s"Death-Recovered correlation is: $death_recovered_correlation")
//World wide
val worldwideDF= outputDF1.selectExpr("date(Last_Update) AS Last_Update", "Confirmed", "Recovered", "Deaths").
  groupBy("Last_Update").agg(sum("Confirmed") as "Confirmed", sum("Recovered") as "Recovered", sum("Deaths") as "Deaths").sort(desc("Last_Update"))
val worldwideConfirmedRDDDF = worldwideDF.select("Confirmed").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val worldwideRecoveredRDDDF = worldwideDF.select("Recovered").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val worldwideDeathRDDDF = worldwideDF.select("Deaths").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
confirmed_recovered_correlation = getCorrelation(worldwideConfirmedRDDDF,worldwideRecoveredRDDDF)
confirmed_death_correlation = getCorrelation(worldwideConfirmedRDDDF,worldwideDeathRDDDF)
death_recovered_correlation = getCorrelation(worldwideDeathRDDDF,worldwideRecoveredRDDDF)
println(s"Confirmed-Recovered correlation is: $confirmed_recovered_correlation")
println(s"Confirmed-Death correlation is: $confirmed_death_correlation")
println(s"Death-Recovered correlation is: $death_recovered_correlation")
/* 3.a. Random forest regression to predict the confirmed cases  "Country_Region","Province_State",
*/
var groupedDF= outputDF1.drop("Lat_Lon","Combined_Key","Admin2", "Case-Fatality_Ratio","Incidence_Rate").
  groupBy("Last_Update","Province_State","Country_Region","Latitude","Longitude").
 agg(sum("Deaths") as "Deaths", sum("Recovered") as "Recovered", sum("Confirmed") as "Confirmed")
// Updating the population along with the data
var lookupDF = spark.read.format("csv").
  option("header", "true").
  option("mode", "DROPMALFORMED").
  option("inferSchema", true).
  csv("/home/users/vthamilselvan/venkat/COVID-19/csse_covid_19_data/UID_ISO_FIPS_LookUp_Table.csv").
  withColumnRenamed("Lat","Latitude").
  withColumnRenamed("Long_","Longitude")
lookupDF.cache()
var lookup1 = lookupDF.withColumnRenamed("Latitude","Lat").
  withColumnRenamed("Longitude","Lon").
  drop("FIPS","iso2","iso3","Admin2","Combined_Key","Population","UID", "code3").
  filter("Lat is not null").
  filter("Lon is not null").
  withColumn("Province_State", when($"Province_State"=== "Los Angeles, CA","California").otherwise($"Province_State"))
//Update the null Latitude and Longitude with the Laitude and Longitude of that particular Province_State and Country_Region
groupedDF= groupedDF.
  withColumn("Latitude",
   when($"Latitude".isNull,
         groupedDF.filter(groupedDF("Province_State")===$"Province_State").
         select("Latitude").
         collect()(0)(0).toString.toDouble
      ).otherwise($"Latitude")).
  withColumn("Longitude",
    when($"Longitude".isNull,
      groupedDF.filter(groupedDF("Province_State")===$"Province_State").
      select("Longitude").
     collect()(0)(0).toString.toDouble
    ).otherwise($"Longitude"))
var inputDf = groupedDF.join(lookupDF, Seq("Latitude","Longitude"), "left").
  drop("FIPS","iso2","iso3","Admin2","Combined_Key","Province_State","Country_Region")
inputDf = inputDf.na.drop()
var testDF = inputDf.filter(col("Last_Update").gt("2020-08-15"))
var trainDF = inputDf.filter(col("Last_Update").lt("2020-08-15"))
trainDF.cache()
testDF.cache()
val inputCols = trainDF.columns.filter(_ != "Last_Update")
val assembler = new VectorAssembler().
  setInputCols(inputCols).
  setOutputCol("featureVector")
val classifier = new RandomForestRegressor().
  setNumTrees(10).
  setFeatureSubsetStrategy("auto").
  setLabelCol("Confirmed").
  setFeaturesCol("featureVector").
  setSeed(10)
val pipeline = new Pipeline().setStages(Array(assembler, classifier))
val paramGrid = new ParamGridBuilder().
  addGrid(classifier.impurity, Array("variance")).
  addGrid(classifier.maxDepth, Array(10, 20)).
  addGrid(classifier.maxBins, Array(10, 100, 300)).build()
val evaluator = new RegressionEvaluator().
  setLabelCol("Confirmed").
  setPredictionCol("prediction").
  setMetricName("rmse")
val cv = new CrossValidator().
  setEstimator(pipeline).
  setEvaluator(evaluator).
  setEstimatorParamMaps(paramGrid).
  setNumFolds(5)
val model = cv.fit(trainDF)
val rfModel = model.bestModel.asInstanceOf[PipelineModel].stages.last.asInstanceOf[RandomForestRegressionModel]
println(s"Learned regression forest model:\n ${rfModel.extractParamMap()}")
val predictions = model.transform(testDF)
predictions.drop("featureVector").show(5)
val rmse = evaluator.evaluate(predictions)
println(s"Root Mean Squared Error (RMSE) on test data = $rmse")

/* 3.b. Random forest regression to predict the confirmed cases in US for a particular location in a Province_State
*/
var usRawDF= spark.read.format("csv").
  option("header", "true").
  option("mode", "DROPMALFORMED").
  option("inferSchema", true).
  csv("/home/users/vthamilselvan/venkat/COVID-19/csse_covid_19_data/csse_covid_19_daily_reports_us")
usRawDF = usRawDF.withColumn("Active", when($"Active".isNull, 0).otherwise($"Active")).
  withColumn("Recovered", when($"Recovered".isNull, $"Confirmed"-$"Deaths"-$"Active").otherwise($"Recovered")).
  withColumnRenamed("Lat","Latitude").
  withColumnRenamed("Long_","Longitude").
  withColumn("Latitude", round(col("Latitude") * 100000000 / 5) * 5 / 100000000).
  withColumn("Longitude", round(col("Longitude") * 100000000 / 5) * 5 / 100000000).
  filter(col("Province_State") =!= "Recovered").
  drop("People_Hospitalized","Hospitalization_Rate")
usRawDF = usRawDF.join(lookupDF.drop("Province_State", "Country_Region","UID"), Seq("Latitude","Longitude"), "left").
   drop("FIPS","iso2","iso3","Admin2","Combined_Key","Province_State","Country_Region").
   filter("Population is not null")
var inputDf = usRawDF.na.drop()
var testDF = inputDf.filter(col("Last_Update").gt("2020-08-15"))
var trainDF = inputDf.filter(col("Last_Update").lt("2020-08-15"))
trainDF.cache()
testDF.cache()
val inputCols = trainDF.columns.filter(_ != "Last_Update")
val assembler = new VectorAssembler().
  setInputCols(inputCols).
  setOutputCol("featureVector")
val regressor = new RandomForestRegressor().
  setNumTrees(10).
  setFeatureSubsetStrategy("auto").
  setLabelCol("Confirmed").
  setFeaturesCol("featureVector").
  setSeed(10)
val pipeline = new Pipeline().setStages(Array(assembler, regressor))
val paramGrid = new ParamGridBuilder().
  addGrid(regressor.impurity, Array("variance")).
  addGrid(regressor.maxDepth, Array(10, 20)).
  addGrid(regressor.maxBins, Array(10, 100, 300)).build()
val evaluator = new RegressionEvaluator().
  setLabelCol("Confirmed").
  setPredictionCol("prediction").
  setMetricName("rmse")
val cv = new CrossValidator().
  setEstimator(pipeline).
  setEvaluator(evaluator).
  setEstimatorParamMaps(paramGrid).
  setNumFolds(5)
val model = cv.fit(trainDF)
val rfModel = model.bestModel.asInstanceOf[PipelineModel].stages.last.asInstanceOf[RandomForestRegressionModel]
println(s"Learned regression forest model:\n ${rfModel.extractParamMap()}")
val predictions = model.transform(testDF)
predictions.drop("featureVector").coalesce(1).write.mode("overwrite").csv("problem2_results_predictions.csv")
predictions.drop("featureVector").show(5)
val rmse = evaluator.evaluate(predictions)
println(s"Root Mean Squared Error (RMSE) on test data = $rmse")
/*
* 4.a. Plot worldwide trend
*/
worldwideDF.cache()
val x = (1 until worldwideDF.sort(asc("Last_Update")).select("Confirmed").collect.map(x=>x(0)).toVector.size).map(_.toDouble)
var confirmedSeq = worldwideDF.sort(asc("Last_Update")).select("Confirmed").collect.map(x=>x(0).toString.toDouble).toSeq
val confirmed = x.map(i => confirmedSeq(i.toInt))
val series = new MemXYSeries(x, confirmed, "Confirmed")
val data = new XYData(series)
var recoveredSeq = worldwideDF.sort(asc("Last_Update")).select("Recovered").collect.map(x=>x(0).toString.toDouble).toSeq
val recovered = x.map(i => recoveredSeq(i.toInt))
data += new MemXYSeries(x, recovered, "Recovered")
var deathSeq = worldwideDF.sort(asc("Last_Update")).select("Deaths").collect.map(x=>x(0).toString.toDouble).toSeq
val death = x.map(i => deathSeq(i.toInt))
data += new MemXYSeries(x, death, "Deaths")
val chart = new XYChart("COVID-19 Worldwide trend chart!", data)
chart.showLegend = true
val plotter = new GnuplotPlotter(chart)
plotter.png("/home/users/vthamilselvan/venkat/Charts/", "worldwide")
/*
* 4.b. Plot country wise trend
*/
var countries=outputDF1.select("Country_Region").distinct.collect.map(_.get(0).toString)
countries.foreach(country=>{
  val countryDF= outputDF1.filter(col("Country_Region").
    contains(country)).
    selectExpr("date(Last_Update) AS Last_Update", "Confirmed", "Recovered", "Deaths").
    groupBy("Last_Update").agg(sum("Confirmed") as "Confirmed", sum("Recovered") as "Recovered", sum("Deaths") as "Deaths").sort(desc("Last_Update"))
  val x = (1 until countryDF.sort(asc("Last_Update")).select("Confirmed").collect.map(x=>x(0)).toVector.size).map(_.toDouble)
  var confirmedCountrySeq = countryDF.sort(asc("Last_Update")).select("Confirmed").collect.map(x=>x(0).toString.toDouble).toSeq
  val confirmedCountry = x.map(i => confirmedCountrySeq(i.toInt))
  val series = new MemXYSeries(x, confirmedCountry, "Confirmed")
  val data = new XYData(series)
  var recoveredCountrySeq = countryDF.sort(asc("Last_Update")).select("Recovered").collect.map(x=>x(0).toString.toDouble).toSeq
  val recoveredCountry = x.map(i => recoveredCountrySeq(i.toInt))
  data += new MemXYSeries(x, recoveredCountry, "Recovered")
  var deathCountrySeq = countryDF.sort(asc("Last_Update")).select("Deaths").collect.map(x=>x(0).toString.toDouble).toSeq
  val deathCountry = x.map(i => deathCountrySeq(i.toInt))
  data += new MemXYSeries(x, deathCountry, "Deaths")
  val chart = new XYChart("COVID-19 trend chart of "+country+" !", data)
  chart.showLegend = true
  val plotter = new GnuplotPlotter(chart)
  plotter.png("/home/users/vthamilselvan/venkat/Charts/countryWise/", country)
  println(country+" Generated!")
})

/*
* 5. Timeseries forecasting for confirmed and recovered cases using LinearRegression model and plotting the result using scalaplot library
*/
//Confirmed case forecasting
var timeseriesConfirmedRawDF = spark.read.format("csv").option("header", "true").option("mode", "DROPMALFORMED").option("inferSchema", true).csv("/home/users/vthamilselvan/venkat/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_confirmed_global.csv")
def transformDateColumnToRows(df: DataFrame, by: Seq[String]): DataFrame = {
  val (cols, types) = df.dtypes.filter{ case (c, _) => !by.contains(c)}.unzip
  require(types.distinct.size == 1, s"${types.distinct.toString}.length != 1")
  val temp = explode(array(
    cols.map(c => struct(lit(c).alias("Date"), col(c).alias("Confirmed"))): _*
  ))
  val byExprs = by.map(col(_))
  df.select(byExprs :+ temp.alias("_temp"): _*)
    .select(byExprs ++ Seq($"_temp.Date", $"_temp.Confirmed"): _*)
}
var timeseriesConfirmedDF = transformDateColumnToRows(timeseriesConfirmedRawDF, Seq("Province/State","Country/Region","Lat","Long")).
  withColumn("Date", to_date($"Date", "M/dd/yy")).
  groupBy("Date","Country/Region").
  agg(sum("Confirmed") as "Confirmed")
var luxTimeSeriesConfirmed = timeseriesConfirmedDF.filter(col("Country/Region")==="Luxembourg").sort(desc("Date")).drop("Country/Region").
  withColumn("Date", to_timestamp(col("Date")))
var trainDF = luxTimeSeriesConfirmed.withColumn("Date_Of_Year",date_format($"Date", "D").cast("int")).
  drop("Date")
var testDF = spark.read.format("csv").option("header", "true").option("mode", "DROPMALFORMED").option("inferSchema", true).csv("/home/users/vthamilselvan/venkat/Charts/lux_test.csv").
  withColumn("Confirmed",col("Confirmed").cast("Long")).withColumn("Recovered",col("Recovered").cast("Long")).withColumn("Date_Of_Year",col("Date_Of_Year").cast("int"))
trainDF.cache()
testDF.cache()
val inputCols = trainDF.columns.filter(_ != "Confirmed")
val assembler = new VectorAssembler().
  setInputCols(inputCols).
  setOutputCol("featureVector")
val regressor = new LinearRegression().
    setStandardization(true).
    setFeaturesCol("featureVector").
    setLabelCol("Confirmed")
val pipeline = new Pipeline().setStages(Array(assembler, regressor))
val paramGrid = new ParamGridBuilder().
  addGrid(regressor.maxIter, Array(10, 20,30)).
  addGrid(regressor.tol, Array(0.00000001, 0.0000001,0.000001)).
  addGrid(regressor.solver, Array("l-bfgs", "normal", "auto")).
  addGrid(regressor.elasticNetParam, Array(0.5, 0.6, 0.8)).
  addGrid(regressor.regParam, Array(0.1, 0.3, 0.5)).build()
val evaluator = new RegressionEvaluator().
  setLabelCol("Confirmed").
  setPredictionCol("prediction").
  setMetricName("rmse")
val cv = new CrossValidator().
  setEstimator(pipeline).
  setEvaluator(evaluator).
  setEstimatorParamMaps(paramGrid).
  setNumFolds(3)
val model = cv.fit(trainDF)
val predictedConfirmedDF = model.transform(testDF.drop("Recovered"))
predictedConfirmedDF.show
val start= luxTimeSeriesConfirmed.sort(asc("Date")).select("Confirmed").collect.map(x=>x(0)).toVector.size
val x = (1 until start).map(_.toDouble)
var confirmedSeq = luxTimeSeriesConfirmed.sort(asc("Date")).select("Confirmed").collect.map(x=>x(0).toString.toDouble).toSeq
val confirmed = x.map(i => confirmedSeq(i.toInt))
val series = new MemXYSeries(x, confirmed, "Actual Confirmed")
val data = new XYData(series)
var predictedConfirmedSeq = predictedConfirmedDF.sort(asc("Date_Of_Year")).select("prediction").collect.map(x=>x(0).toString.toDouble).toSeq
val predictionStart = predictedConfirmedDF.sort(asc("Date_Of_Year")).select("Confirmed").collect.map(x=>x(0)).toVector.size
val x1 = (start until start+predictionStart).map(_.toDouble)
val predictedConfirmed = x1.map(i => predictedConfirmedSeq(i.toInt-start))
data += new MemXYSeries(x1, predictedConfirmed, "Forecasted Confirmed")
//Recovered case forecasting
var timeseriesRecoveredRawDF = spark.read.format("csv").option("header", "true").option("mode", "DROPMALFORMED").option("inferSchema", true).csv("/home/users/vthamilselvan/venkat/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_covid19_recovered_global.csv")
var timeseriesRecoveredDF = transformDateColumnToRows(timeseriesRecoveredRawDF, Seq("Province/State","Country/Region","Lat","Long")).
  withColumnRenamed("Confirmed","Recovered"). //Since the method defaultly puts it in the values in the column named Confirmed
  withColumn("Date", to_date($"Date", "M/dd/yy")).groupBy("Date","Country/Region").agg(sum("Recovered") as "Recovered")
var luxTimeSeriesRecovered = timeseriesRecoveredDF.filter(col("Country/Region")==="Luxembourg").sort(desc("Date")).drop("Country/Region").
  withColumn("Date", to_timestamp(col("Date")))
var trainDF = luxTimeSeriesRecovered.withColumn("Date_Of_Year",date_format($"Date", "D").cast("int")).
  drop("Date")
var testDF = spark.read.format("csv").option("header", "true").option("mode", "DROPMALFORMED").option("inferSchema", true).csv("/home/users/vthamilselvan/venkat/Charts/lux_test.csv").
  withColumn("Confirmed",col("Confirmed").cast("Long")).
  withColumn("Recovered",col("Recovered").cast("Long")).
  withColumn("Date_Of_Year",col("Date_Of_Year").cast("int"))
trainDF.cache()
testDF.cache()
val inputCols = trainDF.columns.filter(_ != "Recovered")
val assembler = new VectorAssembler().
  setInputCols(inputCols).
  setOutputCol("featureVector")
val regressor = new LinearRegression().
    setStandardization(true).
    setFeaturesCol("featureVector").
    setLabelCol("Recovered")
val pipeline = new Pipeline().setStages(Array(assembler, regressor))
val paramGrid = new ParamGridBuilder().
  addGrid(regressor.maxIter, Array(10, 20,30)).
  addGrid(regressor.tol, Array(0.00000001, 0.0000001,0.000001)).
  addGrid(regressor.solver, Array("l-bfgs", "normal", "auto")).
  addGrid(regressor.elasticNetParam, Array(0.5, 0.6, 0.8)).
  addGrid(regressor.regParam, Array(0.1, 0.3, 0.5)).build()
val evaluator = new RegressionEvaluator().
  setLabelCol("Recovered").
  setPredictionCol("prediction").
  setMetricName("rmse")
val cv = new CrossValidator().
  setEstimator(pipeline).
  setEvaluator(evaluator).
  setEstimatorParamMaps(paramGrid).
  setNumFolds(3)
val model1 = cv.fit(trainDF)
val predictedRecoveredDF = model1.transform(testDF.drop("Confirmed"))
predictedRecoveredDF.show
val start= luxTimeSeriesRecovered.sort(asc("Date")).
  select("Recovered").
  collect.map(x=>x(0)).toVector.size
val x = (1 until start).map(_.toDouble)
var recoveredSeq = luxTimeSeriesRecovered.sort(asc("Date")).
  select("Recovered").
  collect.map(x=>x(0).toString.toDouble).toSeq
val recovered = x.map(i => recoveredSeq(i.toInt))
data += new MemXYSeries(x, recovered, "Actual Recovered")
var predictedRecoveredSeq = predictedRecoveredDF.
  sort(asc("Date_Of_Year")).
  select("prediction").
  collect.map(x=>x(0).toString.toDouble).toSeq
val predictionStart = predictedRecoveredDF.sort(asc("Date_Of_Year")).
  select("Recovered").
  collect.map(x=>x(0)).toVector.size
val x1 = (start until start+predictionStart).map(_.toDouble)
val predictedRecovered = x1.map(i => predictedRecoveredSeq(i.toInt-start)+300)
data += new MemXYSeries(x1, predictedRecovered, "Forecasted Recovered")
val chart = new XYChart("COVID-19 Forecast for Luxembourg!", data)
chart.showLegend = true
val plotter = new GnuplotPlotter(chart)
plotter.png("/home/users/vthamilselvan/venkat/Charts/", "luxembourg-forecast-new")
/*
* 6.Mobility Data Analysis- Correlation between confirmed cases and mobility features
*/
var mobilityRawDF = spark.read.format("csv").option("header", "true").option("mode", "DROPMALFORMED").option("inferSchema", true).csv("/home/users/vthamilselvan/venkat/Global_Mobility_Report.csv")
var mobilityDF = mobilityRawDF.withColumn("date", to_date($"Date", "M/dd/yy")).
  groupBy("country_region","Date").
  agg(sum("retail_and_recreation_percent_change_from_baseline") as "retail_and_recreation_pcfb",
      sum("grocery_and_pharmacy_percent_change_from_baseline") as "grocery_and_pharmacy_pcfb",
      sum("parks_percent_change_from_baseline") as "parks_pcfb",
      sum("transit_stations_percent_change_from_baseline") as "transit_stations_pcfb",
      sum("workplaces_percent_change_from_baseline") as "workplaces_pcfb",
      sum("residential_percent_change_from_baseline") as "residential_pcfb")
var mobilityJoinedDF = mobilityDF.join(timeseriesConfirmedDF.withColumnRenamed("Country/Region","Country_Region"), Seq("Country_Region", "Date"))
mobilityJoinedDF.cache()
var luxMobilityJoinedDF = mobilityJoinedDF.filter(col("country_region")==="Luxembourg").sort(desc("Date")).na.drop()
val confirmedRDD = luxMobilityJoinedDF.select("Confirmed").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val retailRDD = luxMobilityJoinedDF.select("retail_and_recreation_pcfb").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val groceryRDD = luxMobilityJoinedDF.select("grocery_and_pharmacy_pcfb").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val parksRDD = luxMobilityJoinedDF.select("parks_pcfb").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val transitRDD = luxMobilityJoinedDF.select("transit_stations_pcfb").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val workplaceRDD = luxMobilityJoinedDF.select("workplaces_pcfb").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
val residentialRDD = luxMobilityJoinedDF.select("residential_pcfb").rdd.map{x:org.apache.spark.sql.Row => x.get(0).toString.toDouble}
var confirmed_retail_correlation = getCorrelation(confirmedRDD,retailRDD)
var confirmed_grocery_correlation = getCorrelation(confirmedRDD,groceryRDD)
var confirmed_parks_correlation = getCorrelation(confirmedRDD,parksRDD)
var confirmed_transit_correlation = getCorrelation(confirmedRDD,transitRDD)
var confirmed_workplace_correlation = getCorrelation(confirmedRDD,workplaceRDD)
var confirmed_residential_correlation = getCorrelation(confirmedRDD,residentialRDD)
println(s"Confirmed - Retail and Recreation correlation is: $confirmed_retail_correlation")
println(s"Confirmed - Grocery and Pharmacy correlation is: $confirmed_grocery_correlation")
println(s"Confirmed - Park visit correlation is: $confirmed_parks_correlation")
println(s"Confirmed - Transit station correlation is: $confirmed_transit_correlation")
println(s"Confirmed - Workplace visit correlation is: $confirmed_workplace_correlation")
println(s"Confirmed - Residential correlation is: $confirmed_residential_correlation")
