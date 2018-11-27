// The OAuth2.0 client ID from the Google Developers Console.
var SERVICE_HOST = "http://tongue.cs.colostate.edu:8080/galileo-web-service";
//var CLIENT_ID = '492266571222-lk6vohmrf2fkkvkmjfi5583qj30ijjkc.apps.googleusercontent.com';
//var APIKEY = "AIzaSyBmgPNSfhXOslSk0fRyf7t0NG7lZ5s7d8s";
//var FUSION_TABLE_ID = "1hUiW58GkgIM1A5lWPeN4xwL1TXaxm7RMYaMsYt6s";
var isserviceup; //will be true if service is up and running
var CLIENT_ID = '492266571222-lk6vohmrf2fkkvkmjfi5583qj30ijjkc.apps.googleusercontent.com';
var SCOPES = ['https://www.googleapis.com/auth/drive.readonly'];
var map;
var ch4layer;
var ch4layerPosition;
var data = [];
var polygons = [];
var selectedPolygon;
var plots = [];
var timer;
var ch4layers = {};
//var features = ['avgTemp', 'avgCO2', 'avgHumidity'];
var features = [];
var geoJSON;
var showingData = false;
var zoom_changed = false;
var globalInfoWindow;
var dataLayer;
// needed for handling polygon drawing
var drewPolygon;
var lastPolygon;
var _drewPolygon;
var previousDataLayer;
var zoom_changed = false;
var modalClosed;
var lastOverlay;
var yaxes = 1;
var axisoptions;
var jsonData;
var axiscolors = ['#FF00FF', '#F88017', '#168EF7', '#006400'];

function csrfSafeMethod(method) {
    // these HTTP methods do not require CSRF protection
    return (/^(GET|HEAD|OPTIONS|TRACE)$/.test(method));
}

function sameOrigin(url) {
    // test that a given url is a same-origin URL
    // url could be relative or scheme relative or absolute
    var host = document.location.host; // host + port
    var protocol = document.location.protocol;
    var sr_origin = '//' + host;
    var origin = protocol + sr_origin;
    // Allow absolute or scheme relative URLs to same origin
    return (url == origin || url.slice(0, origin.length + 1) == origin + '/') ||
        (url == sr_origin || url.slice(0, sr_origin.length + 1) == sr_origin + '/') ||
        // or any other URL that isn't scheme relative or absolute i.e relative.
        !(/^(\/\/|http:|https:).*/.test(url));
}


$(document).ready(function () {
    // Attempt to authenticate for Earth Engine using existing credentials.
    // ee.data.authenticate(CLIENT_ID, initializeGEE, null, null, onImmediateFailed);
    initializeGEE();
//To focus on the input upon showing the modal
    $('#polygon-modal').on('shown.bs.modal', function () {
        $('#polygon-name').focus();
    });

    //to remove the polygon from map if it is not saved
    $('#polygon-modal').on('hidden.bs.modal', function () {
        if (!modalClosed && lastPolygon != undefined)
            lastOverlay.setMap(null);
    });

    //making enter press as save button click
    $("#polygon-name").keyup(function (event) {
        if (event.keyCode == 13) {
            $("#save-polygon").click();
        }
    });

    features = getFeatures();
    $('#save-polygon').click(function () {
        var polygonName = $('#polygon-name').val()
        if (polygonName != undefined && polygonName.length != 0 && lastPolygon != undefined) {
            $('#polygon-modal').modal('hide');
            modalClosed = true;
            $.ajax({
                type: "POST",
                url: "/savepolygon",
                data: JSON.stringify({'name': polygonName, 'polygon': lastPolygon}),
                success: function (response) {
                    $('#polygonid').append('<option value="' + response.id + '" data-polygon=\'' + response.json + '\'>'
                        + response.name + '</option>').selectpicker('refresh');
                    /*$('#polygon').append('<option value="' + response.id + '" data-polygon=\'' + response.json + '\'>'
                     + response.name + '</option>').selectpicker('refresh');*/
                },
                error: function (jqxhr) {
                    $.growl.error({
                        title: 'Something went wrong!',
                        message: jqxhr.responseText + ". If the issue persists, please seek support.",
                        location: 'tc',
                        delayOnHover: true,
                        duration: 5000
                    });
                    lastOverlay.setMap(null);
                }
            });
        }
    });

    
    $('#addY').click(function () {
        if(yaxes < 4) {
            yaxes++;
            $('#yaxes').append("<select id='y" + yaxes + "' class='selectpicker show-menu-arrow show-tick' data-size='5' data-width='24%'\
                            title='Choose a feature'><option>max_temperature</option><option>min_temperature</option>\
			<option>avg_temperature</option><option>std_temperature</option><option>max_humidity</option>\
			<option>min_humidity</option><option>avg_humidity</option><option>std_humidity</option>\
			<option>max_CO2</option><option>min_CO2</option><option>avg_CO2</option>\
			<option>std_CO2</option><option>max_r1</option><option>min_r1</option>\
			<option>avg_r1</option><option>std_r1</option><option>max_r2</option>\
			<option>min_r2</option><option>avg_r2</option><option>std_r2</option>\
			<option>max_r3</option><option>min_r3</option><option>avg_r3</option>\
			<option>std_r3</option><option>max_r4</option><option>min_r4</option>\
			<option>avg_r4</option><option>std_r4</option><option>rep</option>\
                             </select>&nbsp;");
            $('#y'+yaxes).html(axisoptions);
            $('#y'+yaxes).selectpicker('refresh');
        }
        return false;
    });

    $('#hideFeatures').click(function () {
        $('#hideFeatures').addClass('hidden');
        $('#features').addClass('hidden');
        $('#showFeatures').removeClass('hidden');
    });

    $('#showFeatures').click(function () {
        $('#hideFeatures').removeClass('hidden');
        $('#features').removeClass('hidden');
        $('#showFeatures').addClass('hidden');
    });

    var csrftoken = $.cookie('csrftoken');
    $.ajaxSetup({
        beforeSend: function(xhr, settings) {
            if (!csrfSafeMethod(settings.type) && sameOrigin(settings.url)) {
                // Send the token to same-origin, relative URLs only.
                // Send the token only if the method warrants CSRF protection
                // Using the CSRFToken value acquired earlier
                xhr.setRequestHeader("X-CSRFToken", csrftoken);
            }
        }
    });

    $('#polygonid').on('change', function () {
        var polygonJSON = $(this).find("option:selected").data("polygon");
	currentPolygon = polygonJSON;
        if (_drewPolygon != undefined)
            _drewPolygon.setMap(null);
        var paths = [];
        var bounds = new google.maps.LatLngBounds(); //bounding box of the polygon
        $.each(polygonJSON, function (index, item) {
            var lat = parseFloat(item.lat);
            var lng = parseFloat(item.lon);
            paths.push({'lat': lat, 'lng': lng});
            bounds.extend(new google.maps.LatLng(lat, lng));
        });
        // Construct the polygon.
        _drewPolygon = new google.maps.Polygon({
            paths: paths,
            strokeColor: '#FF0000',
            strokeOpacity: 0.8,
            strokeWeight: 2,
            fillOpacity: 0.0,
            clickable: false
        });
        _drewPolygon.setMap(map);
        //set map center to polygon center
        map.fitBounds(bounds);
    });

    $('#show-chart').on('click', function(){
        var selected = new Number($('#plotID').val());
	//var json = JSON.stringify({identifier:"roots", constraint:null, feature:"plotID", op:"==", value:selected, primitive:1, spatial:null, temporal:null, feature:"plotID"})
	var feats = [];	
	for (var i = 1; i <= yaxes; i++) {
       	    var yival =  $('#y'+i).find("option:selected").val();
		feats.push(yival);}
	getPlotFeaturesSeries(selected, feats);
        
    });

    $('#polygon-query').on('click', function () {
	var json = {};
	json.identifier="roots";
	json.spatial=currentPolygon;
	json.constraint=null;
	json.temporal = null;
	$.ajax({
	   type: "POST",
	   url: SERVICE_HOST + "/blocks",// + "/blocks?identifier=roots&spatial="+currentPolygon,
	   dataType: "json",
	   data: JSON.stringify(json),
	   success: function (response){
			console.log(response.result);

	    	}
	    });
    });

    $('#queryselection').on('click', function (){
	var feat = document.getElementsByName('feature')[0].value;
	var op = document.getElementsByName('op')[0].value;
	var val = document.getElementsByName('value')[0].value;
	var primitive;
	console.log(feature);
	if (feat == "genotype")
		primitive = 9;
	else if (feat == "plotID" || feature == "rep")
		primitive = 1;
	else
		primitive = 4;
	console.log(primitive);
	var json = {};
	json.identifier="roots";
	json.constraint={};
	json.constraint.feature = feat;
	json.constraint.primitive=primitive;
	json.constraint.value = val;
	json.constraint.op = op;
	json.spatial=null;
	json.temporal=null;
	$.ajax({
	   type: "POST",
	   url: SERVICE_HOST + "/blocks",
	   dataType: "json",
	   data: JSON.stringify(json),
	   success: function (response){
			console.log(response);

	    	}
	    });
    });

});
//});


function getPlotFeaturesSeries(plotID, feats){
    $.ajax({
	   type: "GET",
	   url: SERVICE_HOST + "/plot?filesystem=roots&plotID="+plotID+"&kind=series&features="+feats,
	   dataType: "json",
	   success: function (response){
		console.log(response.result);
		var type = $('#chartid').val();
        	if(type == undefined)
            	    type = 'line';
        	var xval = buildChartXAxis();
        	var yval = buildChartYAxis();
        	var series = buildChartSeries(response.result);
        	if(xval != 'undefined' && yval != undefined && yval.length != 0){// && series != undefined && series.length != 0) {
            	    var xtext = xval.title.text;
            	    if (xtext == 'Date') {
                	showPolygonChart('Plot ' +plotID + ' Visual Analysis', type, xval, yval, series);
                	//$($.browser.mozilla ? "html" : "body").animate({scrollTop: $("#chart-container").offset().top}, 500);
            	    } else
                	alert('Chosen x-axis(' + xtext + ') for charting is not supported as of now');
            	    } else {
                	alert('One or more of the chosen options for charting are not supported as of now');
            		}

	    	}
	    });
}

$(document).on('click', '#fetchVisFeature', function () {
    var feature = $("#feature").val();
    console.log(feature);
    if (feature == "avgTemp") map.data = null; map.data = addDataLayer("temp");
    if (feature == "avgCO2") map.data = null; map.data = addDataLayer("co2");
    if (feature == "avgHumidity") map.data = null; map.data = addDataLayer("humidity");
});



var onImmediateFailed = function () {
    $('.g-sign-in').removeClass('hidden');
    $('.g-sign-in .button').click(function () {
        ee.data.authenticateViaPopup(function () {
            // If the login succeeds, hide the login button and run the analysis.
            $('.g-sign-in').addClass('hidden');
            $('#searchPlace').removeClass('hidden');
            $('#mapDiv').addClass('col-md-8 align-left');
            $('#optionsDiv').removeClass('hidden');
            $('#spinner').removeClass('hidden');
            initializeGEE();
        });
    });
};

var initializeGEE = function () {
    //$('#spinner').addClass('hidden');
    $('#searchPlace').removeClass('hidden');
    $('#mapDiv').addClass('col-md-8 align-left');
    $('#optionsDiv').removeClass('hidden');
    //ee.initialize();

    //var ftc = ee.FeatureCollection('ft:1hUiW58GkgIM1A5lWPeN4xwL1TXaxm7RMYaMsYt6s');
    /*var minch4 = ftc.aggregate_min('ch4');
     var maxch4 = ftc.aggregate_max('ch4');
     $('#ch4_heading').text("Methane (min: "+minch4.getInfo()+", max: "+maxch4.getInfo()+")");*/

    /*var ft = ee.Feature(ee.Geometry.LineString(ftc.geometry().geometries()));
     var mapId = ft.getMap({'color': 'FF0000'});
     var overlay = new ee.MapLayerOverlay(
     'https://earthengine.googleapis.com/map',
     mapId.mapid, mapId.token, {});*/

    // Show a count of the number of map tiles remaining.
    /*overlay.addTileCallback(function(event) {
     $('.tiles-loading').text(event.count + ' tiles remaining.');
     if (event.count === 0) {
     $('.tiles-loading').empty();
     }
     });*/

    // Show the EE map on the Google Map.
    /*map.overlayMapTypes.push(overlay);
     ch4layer = new google.maps.FusionTablesLayer({
     query: {
     select: 'gps_abs_lat',
     from: '1hUiW58GkgIM1A5lWPeN4xwL1TXaxm7RMYaMsYt6s'
     },
     styles: [{
     where: 'ch4 <= 2.0',
     markerOptions: {
     iconName: "small_yellow"
     }
     }, {
     where: 'ch4 > 2.0 and ch4 <= 2.5',
     markerOptions: {
     iconName: "small_green"
     }
     }, {
     where: 'ch4 > 2.5 and ch4 <= 3.0',
     markerOptions: {
     iconName: "small_purple"
     }
     }, {
     where: 'ch4 > 3.0',
     markerOptions: {
     iconName: "small_red"
     }
     }]
     });
     ch4layer.addListener('click', function(ftme) {
     ftme.infoWindowHtml = '<div class=\'googft-info-window\'> \
     <b>Platform Id: </b> '+ ftme.row['platform_id'].value +'<br> \
     <b>Date: </b> '+ ftme.row['date'].value +'<br> \
     <b>Time: </b> '+ ftme.row['time'].value +'<br> \
     <b>Cavity Pressure: </b>'+ ftme.row['cavity_pressure'].value +'<br> \
     <b>Cavity Temperature: </b> '+ ftme.row['cavity_temp'].value +'<br> \
     <b>Methane: </b> '+ ftme.row['ch4'].value +'<br> \
     <b>GPS Time: </b>'+ ftme.row['gps_time'].value +'<br> \
     <b>Wind North: </b>'+ ftme.row['wind_n'].value +'<br> \
     <b>Wind East: </b> '+ ftme.row['wind_e'].value +'<br> \
     <b>Car Speed: </b> '+ ftme.row['car_speed'].value +'<br> \
     <b>Postal Code: </b> '+ ftme.row['postal_code'].value +'<br> \
     <b>Locality: </b> '+ ftme.row['locality'].value + ' \
     </div>';
     });
     getFeatures(FUSION_TABLE_ID, APIKEY);*/
    //map.setCenter(new google.maps.LatLng(-122.1899, 37.5010));

    //Making a service call to Galileo
    $.when(getLocalities(), getFeatures()).done(function (localityArgs, featureArgs){
        // setting localities data
        var html = [];
        var data = localityArgs[0];
        $(data).each(function (index, val) {
            html.push("<option>" + val + "</option>");
        });
        $('#locality').html(html.join(''));
        $('#locality').selectpicker('refresh');


        // setting features data
        var count = 0;
        html = [];
	//data = featureArgs[0]
        data = ['max_temperature','min_temperature','avg_temperature','std_temperature','max_humidity','min_humidity','avg_humidity',
	'std_humidity','max_CO2','min_CO2','avg_CO2','std_CO2','max_r1','min_r1','avg_r1','std_r1','max_r2','min_r2','avg_r2','std_r2',
	'max_r3','min_r3','avg_r3','std_r3','max_r4','min_r4','avg_r4','std_r4'];
        var options = [];
        var maxcolumns = 4;
        html.push('<table width="100%" class="features"><tr>');
        $(data).each(function (index, val) {
            options.push("<option>" + val + "</option>");
            html.push("<td valign='top' width='" + (100 / maxcolumns) + "%'><label class='checkbox-inline'><input id='" + val + "'  type='checkbox' value='" + val + "' onchange=doOverlay(this);>" + val + "</label></td>");
            count++;
            if (count % maxcolumns == 0)
                html.push("</tr><tr>");
        });
        html.push('</tr></table>');
        $('#features').append(html.join(''));
        axisoptions = options.join('');
        //$('#xaxis').html(axisoptions);
        //$('#y1').html(axisoptions);
        //$('#xaxis').selectpicker('refresh');
        $('#y1').selectpicker('refresh');

        //hide the spinner
        $('#spinner').addClass('hidden');
    });
    /*var polygon = '[{ "lat" : "34.119479019268425", "lon" : "-117.81463623046875"},{ "lat" : "34.12544756511612", "lon" : "-117.54512786865234"},{ "lat" : "33.92285064485909", "lon" : "-117.5533676147461"},{ "lat" : "33.93139678750913", "lon" : "-117.83077239990234"}]';
     getFeatureset(polygon);*/
};

function doOverlay(feature) {
    if (feature.id == 'ch4') {
        if (feature.checked) {
            for (var key in ch4layers) {
                ch4layers[key].setMap(map);
            }
            /*map.overlayMapTypes.push(ch4layer);
             ch4layerPosition = map.overlayMapTypes.getLength() - 1;
             $('#feature_ch4').removeClass('hidden');*/
        } else {
            for (var key in ch4layers) {
                ch4layers[key].setMap(null);
            }
            /*map.overlayMapTypes.removeAt(ch4layerPosition);
             $('#feature_ch4').addClass('hidden');*/
        }
    }

}

function getLocalities() {
    $('#spinner').removeClass('hidden');
    //return $.ajax({url: SERVICE_HOST + "/locality?names", type: 'GET', dataType: 'json'});
    //return $.ajax({url: "/bigquery/?name=localities", type: 'GET', dataType: 'json'});
    return features;
}


function getFeatures() {
    $('#spinner').removeClass('hidden');
    return $.ajax({url: SERVICE_HOST + "/features?filesystem=roots", type:'GET', dataType: 'json'});
    //var table = 'dummyTable';
    //var feats =  $.ajax({url: "/bigquery/?name=features&table="+table, type:'GET', dataType: 'json'});
    //console.log(feats);
    //return feats;
}

function getLocality(locality) {
    $('#spinner').removeClass('hidden');
    //$.get(SERVICE_HOST + "/locality?featureset&locality=" + locality, function (response, status) {
    $.get("/bigquery/?name=locality&city=" + locality, function (response, status) {
        map.setZoom(4);
        var heatMap = [];
        $.each(response, function (index, feature) {
            heatMap.push({
                location: new google.maps.LatLng(feature.gps_abs_lat, feature.gps_abs_lon),
                weight: feature.ch4 * 10
            });
        });
        ch4layers[locality] = new google.maps.visualization.HeatmapLayer({
            data: heatMap,
            radius: map.getZoom() * 3
        });
        $("#ch4").prop('checked', true);
        $("#ch4").trigger("change");
        $('#spinner').addClass('hidden');
    }, "json");
}

/*function getFeatures(ftid, apikey){
 $.get("https://www.googleapis.com/fusiontables/v1/tables/"+ftid+"/columns?key="+apikey, function(data, status){
 var count = 0;
 var html = [];
 var maxcolumns = 4;
 html.push('<table width="100%" class="features"><tr>');
 $.each(data.items, function(key, column){
 if(column.hasOwnProperty('description')){
 html.push("<td valign='top' width='"+(100/maxcolumns)+"%'><label class='checkbox-inline'><input id='" + column.name + "' type='checkbox' value='" + column.name + "' onchange=doOverlay(this);>"+ column.description +"</label></td>");
 count++;
 if(count % maxcolumns == 0)
 html.push("</tr><tr>");
 }
 });
 html.push('</tr></table>');
 $('#features').append(html.join(''));
 }, "json");
 }*/

function showPolygonStats(stats){
    var html = "<table class='table table-bordered'>\
                    <tbody>";
    var columns = 0;
    var statsPresent;
    $.each(stats, function(name, value){
        if(columns % 2 == 0) {
            if (columns == 0) html = html + "<tr>";
            else html = html + "</tr><tr>";
        }
        html = html + "<td width='25%' class='td-heading'>" + name + "</td>"
        html = html + "<td width='25%'>" + String(value).substring(0, 10) + "</td>"
        columns++;
        statsPresent = true;
    });
    html = html + "</tr></tbody></table>";
    if(statsPresent)
        $("#stats").html(html);
    else {
        $("#stats").html('No statistics found for the chosen polygon');
    }
}

function buildChartXAxis(){
    var xaxisVal = $('#xaxis').find("option:selected").val();
    if(xaxisVal != undefined){
        return {
            title: {
                text: xaxisVal
            },
            type: 'datetime', //previously Date
            labels: {
                formatter: function() {
                    return Highcharts.dateFormat('%d-%m-%Y', this.value); //previously %I:%M %P
                }
            },
            tickInterval: 5* 60 * 1000,
            lineWidth: 1,
            lineColor: '#92A8CD',
            tickWidth: 2,
            tickLength: 6,
            tickColor: '#92A8CD'
        };
    }
}

function buildChartYAxis(){
    var y = [];
    for (var i = 1; i <= yaxes; i++) {
       var yival =  $('#y'+i).find("option:selected").val();
       if(yival != 'undefined'){
            if(i % 2 == 0){
                y.push({title: { text: yival, style: {color: axiscolors[y.length]}}, opposite: true, lineColor: axiscolors[y.length], lineWidth: 2, tickWidth: 2, tickLength: 6});
            } else {
                y.push({title: { text: yival,  style: {color: axiscolors[y.length]}}, lineWidth: 2, lineColor: axiscolors[y.length], tickWidth: 2, tickLength: 6});
            }
       }
    }
    return y;

}

function buildChartSeries(json) {
    
    var xval = $('#xaxis').find("option:selected").val();
    var y = [];
    for (var i = 1; i <= yaxes; i++) {
        var yival = $('#y' + i).find("option:selected").val();
        if (yival != undefined)
            y.push(yival);
    }
    var series = []
    if(xval != undefined && y.length != 0){
        for(var i = 0; i < y.length; i++){
            var yval = y[i];
            data = [];
            //$(json).each(function (index, feature) {
                //var datum = [];
                //datum.push(Number(feature.properties[xval]));
                //datum.push(Number(feature.properties[yval]));
                //data.push(datum);
            //});
	    for (var k in json){
		var datum = [];
		var info = json[k].split("->");
		var vals = info[1].split(",");
		for (var j in vals){
		    if (vals[j].split("=")[0] == yval){
			console.log(info[0].replace("-", "").replace("-",""));
			//datum.push(Number(info[0].replace("-", "").replace("-","")));
			datum.push(Date.UTC(Number(info[0].split("-")[0]), Number(info[0].split("-")[1])-1, Number(info[0].split("-")[2])));
			datum.push(Number(vals[j].split("=")[1]));
			data.push(datum);
		    }
		}
	    }
	    data.sort(function(a, b){
    		return a[0] - b[0];
	    });
            series.push({name: yval, color:axiscolors[i], yAxis: i, data: data, lineWidth: 1})
        }
    }
    return series;
}

function showPolygonChart(chartTitle, chartType, xval, yval, series){

    new Highcharts.Chart({
        chart: {
            renderTo: 'chart-container',
            type: chartType,
            borderColor: '#a1a1a1',
            borderWidth: 1,
            borderRadius: 3,
            zoomType: 'x'
        },
        title: {
            text: chartTitle
        },
        legend: {
            borderWidth: 1,
            borderRadius: 3
        },
        xAxis: xval,
        yAxis: yval,
        series: series,
        tooltip: {
            xDateFormat: '%a, %Y %b %e - %I:%M:%S %P',
            borderColor: '#808080',
            shared: true,
            valueSuffix: ' units'
        },
        plotOptions: {
            areaspline: {
                fillOpacity: 0.5
            }
        }
    });
}


function getFeatureset(plotName, plotID) {
    //$('#spinner').removeClass('hidden');
    timer = new Date().getTime();
    $.ajax({
        method: "POST",
        dataType: "json",
        url: SERVICE_HOST+"/featureset",
        data: plotID,
        success: function (response, status, xhr) {
	    
            console.log(response);

            //google chart
            /*var rows = [];
            $(response.geoJSON.features).each(function (index, feature) {
                var row = [];
                row.push((Number(feature.properties.epoch_time) - 1350000000) / 60000);
                row.push(Number(feature.properties.car_speed) / 5);
                row.push(Number(feature.properties.ch4));
                rows.push(row);
            });

            var epochBegin = rows[0][0];
            $(rows).each(function (index, row) {
                row[0] = parseInt(row[0] - epochBegin);
            });

            google.setOnLoadCallback(showChart(rows));*/


            /*var ttr = new Date().getTime();
             $('#ttr').append((ttr - timer) + " ms");
             $('#ttr').removeClass('hidden');
             $('#spinner').addClass('hidden');
             var ftc = ee.FeatureCollection(response);
             ftc = ftc.sort("epoch_time");
             var ft = ee.Feature(ee.Geometry.LineString(ftc.geometry().geometries()));
             var mapId = ft.getMap({'color': '00FFFF'});
             var overlay = new ee.MapLayerOverlay(
             'https://earthengine.googleapis.com/map',
             mapId.mapid, mapId.token, {});
             // Show the EE map on the Google Map.
             map.overlayMapTypes.push(overlay);

             var heatMap = [];
             $.each(response, function(index, feature) {
             heatMap.push({location: new google.maps.LatLng(feature.properties.gps_abs_lat, feature.properties.gps_abs_lon), weight: feature.properties.ch4 * 10});
             });*/

            /*var bufferch4 = function(feature){
             var ch4val = feature.get("ch4");
             ch4val = ee.Number.parse(ch4val).pow(2);
             return feature.buffer(ch4val);
             };

             bftc = ftc.map(bufferch4);*/
            /*mapId = bftc.getMap({'color': 'FFFF00'});
             ch4layer = new ee.MapLayerOverlay(
             'https://earthengine.googleapis.com/map',
             mapId.mapid, mapId.token, {});*/
            /*ch4layer = new google.maps.visualization.HeatmapLayer({
             data: heatMap,
             radius : map.getZoom()*3
             });
             var ttp = new Date().getTime();
             $('#ttp').append((ttp - ttr) + " ms");
             $('#ttp').removeClass('hidden');*/

        }
    });
}

//function to sort multidimensional arrays based on their first column
function comparator(a, b) {
    if (a[0] < b[0]) return -1;
    if (a[0] > b[0]) return 1;
    return 0;
}
function toHex(n) {
    var hex = n.toString(16);
    while (hex.length < 2) {
        hex = "0" + hex;
    }
    return hex;
}
function hslToRgb(hue, sat, light) {
    var t1, t2, r, g, b;
    hue = hue / 60;
    if (light <= 0.5) {
        t2 = light * (sat + 1);
    } else {
        t2 = light + sat - (light * sat);
    }
    t1 = light * 2 - t2;
    r = hueToRgb(t1, t2, hue + 2) * 255;
    g = hueToRgb(t1, t2, hue) * 255;
    b = hueToRgb(t1, t2, hue - 2) * 255;
    return {r: Math.floor(r), g: Math.floor(g), b: Math.floor(b)};
}
function hueToRgb(t1, t2, hue) {
    if (hue < 0) hue += 6;
    if (hue >= 6) hue -= 6;
    if (hue < 1) return (t2 - t1) * hue + t1;
    else if (hue < 3) return t2;
    else if (hue < 4) return (t2 - t1) * (4 - hue) + t1;
    else return t1;
}
function colorGenerator(percent) {
    var rgb = hslToRgb(Math.floor(percent * 300 + 60), 1, 0.5);
    //var rgb = hslToRgb(percent, 1, 0.5)
    return toHex(rgb.r) + toHex(rgb.g) + toHex(rgb.b);
}
//THIS WILL NEED TO CHANGE FOR REAL-WORLD DATA!!!!!!!!!!!!!
function toNum(genotype){
    var sum = 0;
    genotype = genotype.split('');
    for (var i =0; i < genotype.length; i++){
        sum += genotype[i].charCodeAt();
    }
    //For sample data, 243 is minimum, 2989 is maximum
    return (sum-243)/(2989-243);
}
function normalize(val, maximum, minimum){
    return (val - minimum) / (maximum - minimum);
}

function clearMap(){
    if (previousDataLayer != undefined)
        previousDataLayer.setMap(null);

    if (lastOverlay != undefined)
        lastOverlay.setMap(null);

    if (_drewPolygon != undefined)
        _drewPolygon.setMap(null);

}

function getPlotFeatures(plotID){
    $.ajax({
	   type: "GET",
	   url: SERVICE_HOST + "/plot?filesystem=roots&plotID="+plotID+"&kind=summary",
	   dataType: "json",
	   success: function (response){
			console.log("plotID: " + plotID);
			console.log("response: " + response);
			var myHTML =  "<div style='text-align:left; font-family: sans-serif;'>" +
                            "<table style='table-layout: fixed; width: 690px; padding:6px 3px;" +
                            "border-collapse: collapse; border-spacing: 0; border-color: #ccc;'><tr>";
			for (var i in response.result){
			    if (i % 3 == 0){
				myHTML += "</tr><tr>";
			    }
			    var feature = response.result[i].split(":")[0];
			    var value = response.result[i].split(":",2)[1];
			    if (feature == "count" || feature == "ID" || feature == "rep"){
				myHTML += "<td width='115px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                                "color:#333;background-color:#f0f0f0;'>"+feature+"</td>" +
                                "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                                "color:#333;background-color:#ffffff;'><b>" + parseInt(value) + "</b></td>";
			    }
			    else if (feature == "genotype"){
				myHTML += "<td width='115px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                                "color:#333;background-color:#f0f0f0;'>"+feature+"</td>" +
                                "<td width='65px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                                "color:#333;background-color:#ffffff;'><b>" + response.result[i].substring(feature.length+1) + "</b></td>";
			    }
			    else{
			    	myHTML += "<td width='115px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                                "color:#333;background-color:#f0f0f0;'>"+feature+"</td>" +
                                "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                                "color:#333;background-color:#ffffff;'><b>" + parseFloat(value).toFixed(3) + "</b></td>";
			    }
			    
			}
			myHTML += "</table></div>";
			globalInfoWindow.setContent("<div style='width:750px; text-align: center;'>" + myHTML + "</div>");

	    	}
	    });
}

function initPlots(){
    $('div#legendDiv').removeClass('hidden');
    $.ajax({
	type: "GET",
	url: "/static/js/plots.json",
	dataType: "json",
	success: function (response){
		    if (response.what != 'error'){
			dataLayer = new google.maps.Data();
			for (var i in response.features){
			    //console.log(response.features[i].geometry)
			    plots.push(response.features[i])
			    dataLayer.addGeoJson(response.features[i])
			}
			dataLayer.setStyle(function (feature) {
                            var percent = toNum(feature.getProperty("Genotype"));// / feature.getProperty("max-blocks");                 
			    var hexColor = colorGenerator(percent);
                            return {fillColor: "#" + hexColor, strokeWeight: 1, fillOpacity: 0.5};
                        });
			previousDataLayer = dataLayer;
                        previousDataLayer.setMap(map);
			google.maps.event.addListener(dataLayer, 'click', function (event) {
                        var plotID = event.feature.getProperty("ID_Plot");
			getPlotFeatures(plotID);

                        var linearRing = event.feature.getGeometry().getAt(0);
                        var upperLeft = linearRing.getAt(0);
                        var lowerRight = linearRing.getAt(linearRing.getLength() - 2);
                        var position = new google.maps.LatLng((upperLeft.lat() + lowerRight.lat()) / 2,
                            (upperLeft.lng() + lowerRight.lng()) / 2);
                        globalInfoWindow.setPosition(position);
                        //infowindow.setOptions({pixelOffset: new google.maps.Size(0, -30)});
                        globalInfoWindow.open(map);
                    });
			return dataLayer;
		    }
		}
	});
}


function addDataLayer(visType) {
	$('div#legendDiv').removeClass('hidden');
	clearMap();
        $.ajax({
            type: "GET",
            url: "/static/js/plots.json",
            dataType: 'json',
            success: function (response) {
                //hideOverlay();
                if (response.what != 'error') {
                    // Load GeoJSON.
                    dataLayer = new google.maps.Data();
                    //dataLayer.loadGeoJson('https://quarkbackend.com/getfile/johnsoncharles26/overview');
                    dataLayer.addGeoJson(response);
		    if (visType == "genotype"){
                    // Set the stroke width, and fill color for each polygon
                        dataLayer.setStyle(function (feature) {
                            var percent = toNum(feature.getProperty("Genotype"));// / feature.getProperty("max-blocks");                 
			    var hexColor = colorGenerator(percent);
                            return {fillColor: "#" + hexColor, strokeWeight: 1, fillOpacity: 0.5};
                        });
		    }
		    if (visType == "temp"){
			// Set the stroke width, and fill color for each polygon
                        dataLayer.setStyle(function (feature) {
                            var percent = normalize(feature.getProperty("avgTemp"), 85, 75);// / feature.getProperty("max-blocks");                 
			    var hexColor = colorGenerator(percent);
                            return {fillColor: "#" + hexColor, strokeWeight: 1, fillOpacity: 0.5};
                        });
	    	    }
		    if (visType == "co2"){
			// Set the stroke width, and fill color for each polygon
                        dataLayer.setStyle(function (feature) {
                            var percent = normalize(feature.getProperty("avgCO2"), 440, 360);// / feature.getProperty("max-blocks");                 
			    var hexColor = colorGenerator(percent);
                            return {fillColor: "#" + hexColor, strokeWeight: 1, fillOpacity: 0.5};
                        });
	    	    }
		    if (visType == "humidity"){
			// Set the stroke width, and fill color for each polygon
                        dataLayer.setStyle(function (feature) {
                            var percent = normalize(feature.getProperty("avgHumidity"), .85, .6);// / feature.getProperty("max-blocks");                 
			    var hexColor = colorGenerator(percent);
                            return {fillColor: "#" + hexColor, strokeWeight: 1, fillOpacity: 0.5};
                        });
	    	    }
                    google.maps.event.addListener(dataLayer, 'click', function (event) {
                        var plotID = event.feature.getProperty("ID_Plot");
                        var genotype = event.feature.getProperty("Genotype");
                        var Id = event.feature.getProperty("Id");
                        var field = event.feature.getProperty("Field");
			var avgTemp = event.feature.getProperty("avgTemp");
			var avgHumidity = event.feature.getProperty("avgHumidity");
			var avgCO2 = event.feature.getProperty("avgCO2");
                        var myHTML = "<div style='text-align:left; font-family: sans-serif;'>" +
                            "<table style='table-layout: fixed; width: 280px; padding:6px 3px;" +
                            "border-collapse: collapse; border-spacing: 0; border-color: #ccc;'>" +

                            "<tr><td width='65px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#f0f0f0;'>Plot ID</td>" +
                            "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#ffffff;'><b>" + plotID + "</b></td>" +

                            "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#f0f0f0;'>Genotype</td>" +
                            "<td width='55px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#ffffff;'><b>" + genotype + "</b></td></tr><tr>" +

                            "<td width='65px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#f0f0f0;'>ID</td>" +
                            "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#ffffff;'><b>" + Id + "</b></td>" +

                            "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#f0f0f0;'>Field</td>" +
                            "<td width='55px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#ffffff;'><b>" + field + "</b></td></tr><tr>" +

			    "<td width='65px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#f0f0f0;'>Avg. Temperature</td>" +
                            "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#ffffff;'><b>" + (Math.floor(avgTemp * 1000) / 1000) + "</b></td>" +

			    "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#f0f0f0;'>Avg. Humidity</td>" +
                            "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#ffffff;'><b>" + (Math.floor(avgHumidity * 1000) / 1000) + "</b></td></tr>" +

			    "<td width='65px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#f0f0f0;'>Avg. CO2</td>" +
                            "<td width='50px' style='padding:6px 3px; border:1px solid #ccc; overflow:hidden; word-break:normal; " +
                            "color:#333;background-color:#ffffff;'><b>" + (Math.floor(avgCO2 * 1000) / 1000) + "</b></td>" +
                            "</table></div>";
                        globalInfoWindow.setContent("<div style='width:280px; text-align: center;'>" + myHTML + "</div>");
                        var linearRing = event.feature.getGeometry().getAt(0);
                        var upperLeft = linearRing.getAt(0);
                        var lowerRight = linearRing.getAt(linearRing.getLength() - 2);
                        var position = new google.maps.LatLng((upperLeft.lat() + lowerRight.lat()) / 2,
                            (upperLeft.lng() + lowerRight.lng()) / 2);
                        globalInfoWindow.setPosition(position);
                        //infowindow.setOptions({pixelOffset: new google.maps.Size(0, -30)});
                        globalInfoWindow.open(map);
                    });
                    previousDataLayer = dataLayer;
                    previousDataLayer.setMap(map);
                    $('div#legendDiv').removeClass('hidden');
		    return dataLayer;
                } else if (response.what == 'error') {
                    showErrorGrowl('Something went wrong!', response.result + '. If the issue persists, please seek support.');
                }
            },
            error: function () {
                hideOverlay();
                showErrorGrowl('Something went wrong!', 'Unable to get the filesystem details from Galileo. If the issue persists, please seek support');
            }
        });
	
}
function initMap() {
    //Enabling new cartography and themes
    google.maps.visualRefresh = true;
    //Setting starting options of map
    var mapOptions = {
        center: new google.maps.LatLng(14.15964825775016,121.2682859536086),
        zoom: 19,
        mapTypeId: google.maps.MapTypeId.ROADMAP,
        panControl: false,
        zoomControl: true,
        scaleControl: true,
        streetViewControl: false,
        mapTypeControl: true,
        mapTypeControlOptions: {
            style: google.maps.MapTypeControlStyle.HORIZONTAL_BAR,
            position: google.maps.ControlPosition.TOP_LEFT
        },
        zoomControlOptions: {
            style: google.maps.ZoomControlStyle.SMALL,
            position: google.maps.ControlPosition.RIGHT_BOTTOM
        }
    };
    //Getting map DOM element
    var mapElement = $('#mapDiv')[0];
    map = new google.maps.Map(mapElement, mapOptions);
    //map.data.loadGeoJson('/static/js/plots.json');
    //map.data = addDataLayer("genotype");
    map.data = initPlots();
    // Create the search box and link it to the UI element.
    var searchPlace = $('#searchPlace')[0];
    var autocomplete = new google.maps.places.Autocomplete(searchPlace);
    autocomplete.bindTo('bounds', map);
    map.controls[google.maps.ControlPosition.TOP_LEFT].push(searchPlace);
    map.controls[google.maps.ControlPosition.BOTTOM_CENTER].push(document.getElementById('legendDiv'));
    var infowindow = new google.maps.InfoWindow();
    globalInfoWindow = infowindow;
    var marker = new google.maps.Polygon({
        map: map
    });
    marker.addListener('click', function () {
        infowindow.open(map, marker);
    });
    autocomplete.addListener('place_changed', function () {
        infowindow.close();
        var place = autocomplete.getPlace();
        if (!place.geometry) {
            return;
        }
        if (place.geometry.viewport) {
            map.fitBounds(place.geometry.viewport);
        } else {
            map.setCenter(place.geometry.location);
            map.setZoom(10);
        }
    });

    map.addListener('zoom_changed', function () {
        zoom_changed = true;
    });

    map.addListener('idle', function () {
        //unhide floating div when the map loads completely
        $('#floatingDiv').removeClass('hidden');
    });

    var drawingManager = new google.maps.drawing.DrawingManager({
        drawingControl: true,
        drawingControlOptions: {
            position: google.maps.ControlPosition.LEFT_BOTTOM,
            drawingModes: [
                google.maps.drawing.OverlayType.POLYGON,
                google.maps.drawing.OverlayType.RECTANGLE
            ]
        },
        polygonOptions: {
            fillOpacity: 0.0,
            strokeColor: '#FF0000',
            strokeWeight: 2
        },
        rectangleOptions: {
            fillOpacity: 0.0,
            strokeColor: '#FF0000',
            strokeWeight: 2
        }
    });
    drawingManager.setMap(map);
    google.maps.event.addListener(drawingManager, 'overlaycomplete', function (event) {
        drawingManager.setDrawingMode(null);//Default to hand
        //event.overlay.setOptions({fillOpacity: 0.0, strokeColor: '#FF0000'});
        var polygon;
        if (event.type == google.maps.drawing.OverlayType.POLYGON) {
            var latlng = event.overlay.getPath().getArray(); //latlng array
            polygon = "[";
            latlng.forEach(function (location, index) {
                polygon = polygon.concat("{\"lat\":\"" + location.lat() + "\",\"lon\":\"" + location.lng() + "\"}");
                if (index < latlng.length - 1)
                    polygon = polygon.concat(",");
            });
            polygon = polygon + "]";
            lastOverlay = event.overlay;
            lastPolygon = polygon;
            modalClosed = false;
            $('#polygon-name').val("");
            $('#polygon-modal').modal();
        } else {
            //overlay is a rectangle
            var bounds = event.overlay.getBounds();
            var ne = bounds.getNorthEast();
            var sw = bounds.getSouthWest();
            polygon = '[{"lat":"' + ne.lat() + '","lon":"' + ne.lng() + '"},' +
                '{"lat":"' + sw.lat() + '","lon":"' + ne.lng() + '"},' +
                '{"lat":"' + sw.lat() + '","lon":"' + sw.lng() + '"},' +
                '{"lat":"' + ne.lat() + '","lon":"' + sw.lng() + '"}]';
            lastOverlay = event.overlay;
            lastPolygon = polygon;
            modalClosed = false;
            $('#polygon-name').val("");
            $('#polygon-modal').modal();
        }
    });

    google.maps.event.addListener(map, 'click', function () {
        infowindow.close();
    });

    var legendHTML = [];
    for (var i = 0; i <= 25; ++i) {
        legendHTML.push('<span style="color: #' + colorGenerator(i/25) + '">&#9608;</span>');
    }
    $('div#colorLegend').html(legendHTML.join(""));
}

