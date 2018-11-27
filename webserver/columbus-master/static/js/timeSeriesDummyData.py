import random
import datetime
import json
out = open("/s/bach/j/under/mroseliu/dummy.csv", "w+")
out.write("plot_id,date,temperature,humidity,co2,genotype\n")
date = datetime.datetime(2017, 06, 01)

#first read existing data and create a mapping of: <plot_id, genotype>:
with open("/s/parsons/l/sys/www/radix/columbus-master/static/js/plots.json") as f:
	plots = json.load(f)
plot_map = dict()
for feature in plots['features']:
	plot_map.update({feature['properties']['ID_Plot']:feature['properties']['Genotype']})
#to increment date by a day: date+=datetime.timedelta(days=1)

#coverage for each plot
for plot in plot_map:
	#now create one row of data per day for 25 days
	for i in range(25):
		date += datetime.timedelta(days=1)
		out.write(str(plot) + ',' + str(date) + ',' + str(random.uniform(78, 85)) + ',' + str(random.uniform(.6,.85)) + ',' + str(random.uniform(390,410)) + ',' + str(plot_map[plot]) +'\n')
	date = datetime.datetime(2017, 06, 01)
