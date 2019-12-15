/*
 * #%L
 * AVATAR
 * %%
 * Copyright (C) 2015 Agile Knowledge Engineering and Semantic Web (AKSW)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.avatar.statistics;

import java.util.HashMap;
import java.util.Map;

import org.aksw.avatar.clustering.Node;

/**
 *
 * @author ngonga
 */
public class ChiSquaredStats implements Stats {
    
    public Map<? extends Node, Double> computeSignificance(Map<? extends Node, Double> edges)
    {
        double expected = 0d;
        for(Node n: edges.keySet())
        {
            expected = expected + edges.get(n);
        }
        expected = expected/edges.keySet().size();
        
        Map<Node, Double> result = new HashMap<Node, Double>();
        for(Node n: edges.keySet())
        {
            result.put(n, Math.pow(edges.get(n)-expected, 2)/expected);
        }
        return result;
    }    
}
