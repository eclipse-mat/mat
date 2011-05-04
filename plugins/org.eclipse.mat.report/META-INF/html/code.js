/*******************************************************************************
 * Copyright (c) 2008, 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     SAP AG - initial API and implementation
 *******************************************************************************/
function hide(obj, a)
{
	var imageBase = document.getElementById('imageBase');
	var div = document.getElementById(a).style;
	
	if (div.display == "none")
	{
		div.display = "block";
		obj.firstChild.src = imageBase.value + 'opened.gif'
	}
	else
	{
		div.display = "none";
		obj.firstChild.src = imageBase.value + 'closed.gif'
	}
}

function preparepage()
{
	var W3CDOM = document.getElementById && document.createTextNode
	if (!W3CDOM)
		return;

	rendertrees();
	stripetables();
	collapsible();
}

function rendertrees()
{
	var imageBase = document.getElementById('imageBase');

	var tables=document.getElementsByTagName('table');
	for (var i=0;i<tables.length;i++)
	{
		if(tables[i].className!='result')
			continue;
			
		var tbodies = tables[i].getElementsByTagName("tbody");
		for (var h = 0; h < tbodies.length; h++)
		{
			if (tbodies[h].className!='tree')
				continue;

			var trs = tbodies[h].getElementsByTagName("tr");
			for (var ii = 0; ii < trs.length; ii++)
			{
				treerow(imageBase.value, trs[ii]);
			}
		}
	}
}

function treerow(imageBaseValue, element)
{
	var cell = element.firstChild;
	var celltext = cell.firstChild;

	var code = celltext.data;
	
	if (typeof code=='undefined')
		return;
	
	for(var ii=0; ii<code.length; ii++)
	{
		var c = code.charAt(ii);
		
		var replace = document.createElement('img');
		replace.alt = c;

	
		switch(c)
		{
		case "+":
			replace.src = imageBaseValue + "fork.gif";
			break;
		case  ".":
			replace.src = imageBaseValue + "empty.gif";
			break;
		case "\\":
			replace.src = imageBaseValue + "corner.gif";
			break;
		case "|":
			replace.src = imageBaseValue + "line.gif";
			break;
		}
		cell.insertBefore(replace, celltext);
	}
	
	cell.removeChild(celltext);
}

function stripetables()
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


//
// collapsible list items
//

function collapsible()
{
	var imageBase = document.getElementById('imageBase');
	closedImage = imageBase.value + 'closed.gif';
	openedImage = imageBase.value + 'opened.gif';
	nochildrenImage = imageBase.value + 'nochildren.gif';
	
	var uls = document.getElementsByTagName('ul');
	for (var i=0;i<uls.length;i++)
	{
		if(uls[i].className == 'collapsible_opened')
		{
			makeCollapsible(uls[i], 'block', 'collapsibleOpened', openedImage);
		}
		else if(uls[i].className == 'collapsible_closed')
		{
			makeCollapsible(uls[i], 'none', 'collapsibleClosed', closedImage);
		}
	}
}

function makeCollapsible(listElement, defaultState, defaultClass, defaultImage)
{
	listElement.style.listStyle = 'none';

	var child = listElement.firstChild;
	while (child != null)
	{
		if (child.nodeType == 1)
		{
			var list = new Array();
			var grandchild = child.firstChild;
			while (grandchild != null)
			{
				if (grandchild.tagName == 'OL' || grandchild.tagName == 'UL')
				{
					grandchild.style.display = defaultState;
					list.push(grandchild);
				}
				grandchild = grandchild.nextSibling;
			}
			
			var node = document.createElement('img');

			if (list.length == 0)
			{
				node.setAttribute('src', nochildrenImage);
			}
			else
			{
				node.setAttribute('src', defaultImage);
				node.setAttribute('class', defaultClass);
				node.onclick = createToggleFunction(node,list);
			}

			child.insertBefore(node,child.firstChild);
		}

		child = child.nextSibling;
	}
}

function createToggleFunction(toggleElement, sublistElements)
{
	return function()
	{
		if (toggleElement.getAttribute('class')=='collapsibleClosed')
		{
			toggleElement.setAttribute('class','collapsibleOpened');
			toggleElement.setAttribute('src',openedImage);
		}
		else
		{
			toggleElement.setAttribute('class','collapsibleClosed');
			toggleElement.setAttribute('src',closedImage);
		}

		for (var i=0;i<sublistElements.length;i++)
		{
			sublistElements[i].style.display = (sublistElements[i].style.display=='block') ? 'none' : 'block';
		}
	}
}
