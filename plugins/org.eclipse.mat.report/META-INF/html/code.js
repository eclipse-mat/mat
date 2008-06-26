function hide(a) {
	var div =  document.getElementById(a).style;
	if (div.display == "none")
		div.display="block";
	else
		div.display="none";
}

function preparepage()
{
	stripetables();
}

function stripetables()
{
	if (document.getElementById && document.createTextNode)
	{
		var tables=document.getElementsByTagName('table');
		for (var i=0;i<tables.length;i++)
		{
			if(tables[i].className=='result')
			{
				stripe(tables[i]);
			}
		}
	}
}

// this function is need to work around 
// a bug in IE related to element attributes
function hasClass(obj)
{
	var result = false;
	if (obj.getAttributeNode("class") != null)
	{
		result = obj.getAttributeNode("class").value;
	}
	return result;
}   

function stripe(table)
{
	var even = false;
	var evenColor = "#fff";
	var oddColor = "#eee";

	var tbodies = table.getElementsByTagName("tbody");
	for (var h = 0; h < tbodies.length; h++)
	{
		var trs = tbodies[h].getElementsByTagName("tr");
		for (var i = 0; i < trs.length; i++)
		{

			if (!hasClass(trs[i]) && ! trs[i].style.backgroundColor)
			{
				var tds = trs[i].getElementsByTagName("td");
				for (var j = 0; j < tds.length; j++)
				{
        
					var mytd = tds[j];

					if (!hasClass(mytd) && ! mytd.style.backgroundColor)
					{
						mytd.style.backgroundColor = even ? evenColor : oddColor;
					}
				}
			}

			even =  ! even;
		}
    	}
}
