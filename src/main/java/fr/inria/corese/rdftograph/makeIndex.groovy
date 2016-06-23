/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package fr.inria.corese.rdftograph

graph = TitanFactory.open('berkeleyje:/Users/edemairy/btc_titan_10m_v3')
g = graph.traversal()
graph.tx().rollback()
mgmt = graph.openManagement()
value = mgmt.getPropertyKey('value')
rdf_edge = mgmt.getEdgeLabel('rdf_edge')
mgmt.buildIndex('byValueAndLabel', Edge.class).addKey(value).indexOnly(rdf_edge).buildCompositeIndex()
mgmt.commit()

mgmt.awaitGraphIndexStatus(graph, 'byValueAndLabel').call()
//Reindex the existing data
mgmt = graph.openManagement()
mgmt.updateIndex(mgmt.getGraphIndex("byValueAndLabel"), SchemaAction.REINDEX).get()
mgmt.commit()
g.V().has('value', 'http://www.janhaeussler.com/author/jan-haeussler/#foaf').limit(1)