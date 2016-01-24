/* Copyright 2015-2016 Stuart Holland
 *
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
 */
package com.sygmatech.grails.reviews

import grails.util.GrailsNameUtils

class ReviewsTagLib {
    static defaultEncodeAs = [taglib:'html']
    //static encodeAsForTags = [tagName: [taglib:'html'], otherTagName: [taglib:'none']]

    static namespace = "reviews"

    def each = { attrs, body ->
        def bean = attrs.bean
        def varName = attrs.var ?: "review"
        if(bean?.metaClass?.hasProperty(bean, "reviews")) {
            bean.reviews?.each {
                out << body((varName):it)
            }
        }

    }

    def eachRecent = { attrs, body ->
        def domain = attrs.domain
        if(!domain && attrs.bean) domain = attrs.bean?.class
        def varName = attrs.var ?: "review"

        if(domain) {
            domain.recentReviews?.each {
                out << body((varName):it)
            }
        }

    }

    def resources = {
        out << """
        <script type=\"text/javascript\" src=\"${resource(dir: pluginContextPath + '/js', file: 'reviews.js')}\"></script>
        <link rel=\"stylesheet\" href=\"${createLinkTo(dir: pluginContextPath + '/css', file: 'reviews.css')}\" />
        """
    }

    // TODO Decide if I should use render or reviews method
    def render = { attrs, body ->
        def bean = attrs.bean
        def noEscape = attrs.containsKey('noEscape') ? attrs.noEscape : false

        plugin.isAvailable(name:"grails-ui") {
            noEscape = true
        }
        if(bean?.metaClass?.hasProperty(bean, "reviews")) {
            out << g.render(template:"/reviewable/reviews", plugin:"reviewable", model:[reviewable:bean, noEscape:noEscape])
        }

    }

    def reviews = {attrs ->
        if (!attrs.bean) throw new ReviewException("There must be a 'bean' domain object included in the reviews tag.")
        def bean = attrs.bean
        def average = bean.averageReview ?: 0
        def votes = bean.totalReviews
        def type = GrailsNameUtils.getPropertyName(bean.class)
        def id = attrs.id ?: "review"

        // TODO either combine comments body here or combine star rating with comments in comments body


        if (attrs.active == 'false') {
            out << """
            <table class="reviewDisplay">
                <tr>
            """
            def href = attrs.href ? "href=\"${attrs.href}\"" : ''
            5.times {cnt ->
                def i = cnt + 1
                if (average >= i) {
                    out << """<td><div class="star on"><a $href></a></div></td>"""
                } else {
                    def starWidth = 100 * (average - (i - 1))
                    if (starWidth < 0) starWidth = 0
                    out << """<td><div class="star on"><a $href style="width:${starWidth}%"></a></div></td>"""
                }
            }
            out << """
                    <td>(${votes ?: 0})</td>
                </tr>
            </table>
            """
        } else { // Review is active
            out << """
            <div id="${id}_review" class="star_rating">
                <form id="${id}_form" class="star_rating" action="${createLink(controller: 'reviewable', action: 'add', id: bean.id, params: [type: type])}" method="post" title="${average}">
                    <label for="${id}_select">Rating:</label>
                    <select name="review.stars" id="${id}_select">
                        <option value="1">1 - Poor</option>
                        <option value="2">2 - Fair</option>
                        <option value="3">3 - Good</option>
                        <option value="4">4 - Very Good</option>
                        <option value="5">5 - Excellent</option>
                    </select>
                    <input type="text" id="reviewTitle" name="review.title" />
                    <input type="textarea" id="reviewBody" name="review.body" /> <br />

                    <input id="${id}_active" name='active' type="hidden" value="true"/>
                    <input type="hidden" name="reviewLink.reviewRef" value="${bean.id}" />
                    <input type="hidden" name="reviewLink.type" value="${bean.class.name}" />
                    <input type="hidden" name="reviewPageURI" value="${request.forwardURI}"></g:hiddenField>

                    <input type="submit" value="Submit Review"/>
                </form>
            </div>
            <div id='${id}_notifytext'>(${votes ?: 0} Reviews)</div>
            """
        }
    }
}
