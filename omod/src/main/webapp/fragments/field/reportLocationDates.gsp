<%
	def startFieldName = config.startFieldName ?: "param[startDate]";
	def endFieldName = config.endFieldName ?: "param[endDate]";
	def locationFieldName = config.locationFieldName ?: "param[location]";
%>
<script type="text/javascript">
	jQuery(function() {

	});
</script>

<div>
	<b>Start Date: </b> ${ ui.includeFragment("kenyaui", "field/java.util.Date", [ id: "startDate", formFieldName: startFieldName]) }
	<b>End Date: </b> ${ ui.includeFragment("kenyaui", "field/java.util.Date", [ id: "endDate", formFieldName: endFieldName]) }
</div>

<span id="${ config.id }-error" class="error" style="display: none"></span>

<input type="hidden" id="date_value" />
