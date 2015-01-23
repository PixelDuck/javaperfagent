 function loadRecentFiles() {
	$.getJSON("files/recent").done(function( data ) {
		var _rf = $('#recentfiles');
		_rf.html('');
		$.each( data, function(id, value) {
			_a = $('<a>').attr('href', '#').text(value.path);
			_a.click(function(){show(value.path);});
			_rf.append(
				$('<li>')
					.attr('id', 'recentfile-'+id).attr('value', value.path)
					.append(_a));
		});
		if (Object.keys(data).length==0)
			_rf.html('none');
	});
}

function show(path) {
	var rootThreshold = parseFloat($('#rootthreshold').val());
	var threshold = parseFloat($('#threshold').val());
	$('#results').hide();
	$('#loading').show();
	console.log("Root threshold: "+rootThreshold);
	$.getJSON("file/"+path).done(function(data) {
		var _tree = $('#resultsTree');
		_tree.html('');
		$('#filedisplay').text('Results from '+path);
		loadRecentFiles();
		$.each(data, function(id, row) {
			var d = row.duration.substring(0, row.duration.length-2);
			if (d > rootThreshold) {
				var _tr = $('<tr>')
					.addClass('root')
					.attr('id', 'call-'+row.lineNumber)
					.append($('<td>')
						.append($('<span>')
							.text(row.duration)
							.addClass("duration")
							.addClass(toSpeedColor(row.duration, row.duration)))
						.append($('<i>')
							.addClass(row.hasChild ? 'icon-plus-squared-alt' : 'no-icon')
							.css('padding-left', row.hasChild ? '0px' : '18px')
						)
						.append($('<span>')
							.text(row.name)
							.addClass(toSpeedColor(row.duration, row.duration))
						)
					);
				$(this).addClass('expandable');
				_tree.append(_tr);
				if (row.hasChild) {
					_tr.click(function(event){
						$(this).toggleClass('expanded');
						var _icon = $(this).find('i');
						_icon.toggleClass('icon-plus-squared-alt');
						_icon.toggleClass('icon-minus-squared-alt');
						var id = $(this).attr('id');
						var _subcalls = $('#subcalls-'+id);
						if ($(this).hasClass('expanded')) {
							if (_subcalls) {
								var _loadingTr = $(this).after(
									$('<tr>')
										.append($('<td>').addClass('loading-subtree')
											.append($('<i>').addClass("icon-spin1").addClass("animate-spin"))
											.append($('<span>').text('Loading...'))
										)
									);
								expandTree(id.substring(5), $(this), 1, row.duration, threshold);
							} else {
								$('.subcalls-'+id).show();
							}
						} else {
							$('.subcalls-'+id).hide();
						}
					})
				}
			}
		});
		$('#refresh').attr('disabled', false);
		$('#loading').hide();
		$('#results').show();
	});
}

function computePerc(duration, mainDuration) {
	var d = duration.substring(0, duration.length-2);
	var md = mainDuration.substring(0, mainDuration.length-2);
	return md > 0 ? Math.round(d / md * 1000) /10 : '-';
}

function expandTree(id, _parentTr, deep, parentDuration, threshold) {
	$.getJSON("file/current/line?lineNumber="+id).done(function(data) {
		_parentTr.next().remove();
		analyzeSubCallsAndFillTree(id, _parentTr, deep, parentDuration, data.subcalls, threshold);
	});
}

function analyzeSubCallsAndFillTree(id, _parentTr, deep, parentDuration, subcalls, threshold) {
	for (var i=0; i<subcalls.length; i++) {
		var sc = subcalls[i];

		var name, duration, hasChild;
		var nextSubCalls;
		for (var prop in sc) {
			if (prop != 'subcalls') {
				name = prop;
				duration = sc[prop];
			} else {
				hasChild = true;
				nextSubCalls = sc[prop];
			}
		}
		//console.log("analyze "+name+" ["+duration+"]");
		var d = duration.substring(0, duration.length-2);
		if (d > threshold) {
			var perc = computePerc(duration, parentDuration);
			var currentNodeId = 'subcalls-'+id;
			var _tr = $('<tr>')
				.attr('id', currentNodeId)
				.addClass(id)
				.append($('<td>')
					.css('padding-left', (deep * 10) + 'px')
					.append($('<span>')
						.addClass("duration")
						.append($('<span>')
							.text(duration)
							.addClass(toSpeedColor(duration, duration))
						)
						.append($('<span>')
							.text(" ["+perc+'%]')
							.addClass(toPercColor(perc))
						)
					)
					.append($('<i>')
						.addClass(hasChild ? 'icon-minus-squared-alt' : 'no-icon')
						.css('padding-left', hasChild ? '0px' : '18px')
					)
					.append($('<span>')
						.text(name)
						.addClass(toSpeedColor(duration, duration))
					)
				);
			_parentTr.after(_tr);
			if (hasChild) {
				analyzeSubCallsAndFillTree(currentNodeId, _tr, deep+1, parentDuration, nextSubCalls, threshold);
			}
		}
	}
}

function toSpeedColor(duration, parentDuration) {
	var d = duration.substring(0, duration.length-2);
	if(d>1000)
		return 'speed-veryslow';
	if(d>500)
		return 'speed-slow';
	if(d>150)
		return 'speed-medium';
	if(d>40)
		return 'speed-fast';
	return 'speed-fastest';
}

function toPercColor(perc) {
	if(perc>75)
		return 'speed-veryslow';
	if(perc>50)
		return 'speed-slow';
	if(perc>25)
		return 'speed-medium';
	if(perc>10)
		return 'speed-fast';
	return 'speed-fastest';
}


$( document ).ready(function() {
	$('#refresh').attr('disabled', true);
	loadRecentFiles();
	$("#openfile").click(function (){
		show($('#filetoopen').val());
	});
	$( "#filetoopen" ).keypress(function( event ) {
		if ( event.which == 13 ) {
			event.preventDefault();
			show($('#filetoopen').val());
		}
	});
	$("#refresh").click(function (){
		$.getJSON("file/current/path").done(function(data){
			show(data.path);
		});
	 });
	$("#shutdown").click(function (){
		alert("shutdown");
		$.ajax({url:"server/shutdown"}).done(function (){
			alert("server shutdown");
		});
	});
	$('#results').hide();
	$('#loading').hide();
});