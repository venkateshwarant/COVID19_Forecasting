# FORECASTING CASE COUNTS USING REGRESSION MODELS AND AN ANALYSIS ON COVID-19 DATASET

## ABSTRACT
As the COVID-19 pandemic tends to grow in number worldwide, we record huge data related to COVID-19. With the huge computing power which we have today, we can try to analyze the data collected either to find some useful facts or create some machine learning models with which we can predict the growth of pandemic in different parts of the world. In this use-case study, I have taken the John Hopkins datasets [1] to perform data analysis and to create machine learning model (mainly based on Linear regression and Random forest regression) to predict and forecast the growth of the pandemic in a particular locality or a country. Here we mainly predict and forecast the confirmed case count and the recovered case count.

NOTE: View the Report.pdf for the full report

## PROBLEM STATEMENT
In this case study, we are going to do a detailed analysis on the following intriguing concepts- 

1. With this huge data in our hand we should be able to rank the countries based on -

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;a. Current death count

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;b. Current confirmed case count

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;c. Current active case count

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;d. Current recovered case count

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;e. Case-Fatality-Ratio

  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;. Incidence rate

By performing the above analysis, we can understand different dynamics in different countries. For e.g. There may be countries who ranks higher in confirmed cases but lower fatality rate and death count. We can identify the nature of the pandemics in different countries.


2. Finding the correlation between the confirmed case counts, recovered case counts and death counts worldwide and for specific counties. With this analysis, we can understand how the different records correlate with each other.


3. Creating machine learning models to predict the confirmed case counts. These types of machine learning models will help us understand how pandemic is spreading in different regions around the world and help us forecast the confirmed case counts with
some accuracy for some future date. This task includes-

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;a. Creatingrandomforestregressionmodel
with the whole data and predict the confirmed cases for a particular location (using latitude and longitude) at a particular date. We should measure the accuracy by some evaluation metrics such as RMSE or MSE.

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;b. Creating the random forest regression model for the US specific data (using the US specific records in John Hopkins university data) and to predict the confirmed cases on a particular location in a state of the country. This makes sense because the US specific data has many features recorded such as hospitalization rate, testing rate etc.


4. Visualization of the trends-

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;a. Plot the worldwide trend of confirmed, recovered and death counts as a line chart.

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;b. Plot the trends of confirmed, recovered and death counts as a line chart separately for each country.


5. Forecasting the confirmed case count and recovery case count using the linear regression model for Luxembourg for the next 10 days and plotting it as a line chart.


6. Using the Mobility dataset [2] to find the correlation between the confirmed case counts and the following-

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;a. Retail and recreation percent change from baseline

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;b. Grocery and pharmacy percent change from baseline

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;c. Parks percent change from baseline

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;d. Transit stations percent change from baseline

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;e. Workplaces percent change from baseline

 &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;f. Residential percent change from baseline


## Bibliography

[1] J. H. University, "COVID-19 Data Repository by the Center for Systems Science and Engineering (CSSE) at Johns Hopkins University," [Online]. Available: https://github.com/CSSEGISandData/CO VID-19.

[2] Google, "COVID-19 Community Mobility Reports," [Online]. Available: https://www.google.com/covid19/mobilit y/ .

[3] U. o. Luxembourg, "High Performance Computing in Luxembourg," [Online]. Available: https://hpc.uni.lu/.

[4] Sameersingh. [Online]. Available: https://github.com/sameersingh/scalaplot .

[5] J. H. University, "John Hopkins university website for COVID-19 trends," [Online]. Available: https://www.arcgis.com/apps/opsdashboa rd/index.html#/bda7594740fd402994234 67b48e9ecf6.
