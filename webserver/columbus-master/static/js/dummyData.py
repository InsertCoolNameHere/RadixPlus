import random

def createLine(line):
	line = line[:-4]
	line +=', "avgCO2":'+str(random.uniform(390,410))+', "avgHumidity":'+str(random.uniform(.6,.85))+',"avgTemp":'+str(random.uniform(80, 85))+"}},"
	return line

out = open("plots.json", "w+")
infile = open("Plot_Center.json", "r")

lines = infile.readlines()
out.write('{"type":"FeatureCollection", "features": [\n')
for line in lines[1:-1]:
	line = createLine(line)
	out.write("%s\n" % line)
	
out.write("]}")
out.close()

