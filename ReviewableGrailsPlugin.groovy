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

import grails.util.*

class ReviewableGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.2 > *"
    // resources that are excluded from plugin packaging TODO make sure to update as I add tests
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/views/reviewable._reviews.gsp",
            "grails-app/views/test/index.gsp"
    ]

    def title = "Reviewable Plugin" // Headline display name of the plugin
    def author = "Stuart Holland"
    def authorEmail = "stuart@sygmatechnology.com"
    def description = '''\
        A plugin that adds a generic mechanism for reviewing domain objects.  Mark up any of your domain classes as having \
        reviews and then use the tag library to integrate reviews into your views.
    ''' 

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/reviewable"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
    def organization = [ name: "Sygma Technology Solutions", url: "http://sygmatechnology.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPREVIEWABLE" ]

    // Online location of the plugin's browseable source code.
    // TODO add GitHub URL
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    def doWithSpring() {
        def config = application.config

        if(!config.grails.reviewable.reviewer.evaluator) {
            config.grails.reviewable.reviewer.evaluator = { request.user }
        }
    }

    def doWithDynamicMethods = { ctx ->
        for (domainClass in application.domainClasses) {
            if (Reviewable.class.isAssignableFrom(domainClass.clazz)) {
                domainClass.clazz.metaClass {

                    'static' {
                        listOrderByAverageReview { Map params = [:] ->
                            if (params == null) params = [:]
                            def clazz = delegate
                            def type = GrailsNameUtils.getPropertyName(clazz)
                            if (params.cache == null) params.cache = true
                            def results = clazz.executeQuery("select r.reviewRef,avg(review.stars),count(review.stars) as c from ReviewLink as r join r.review as review where r.type='$type' group by r.reviewRef order by count(review.stars) desc, avg(review.stars) desc", params)
                            def instances = clazz.withCriteria(max: params.get("max") ?: 10, offset: params.get("offset") ?: 0) {
                                inList 'id', results.collect { it[0] }
                                cache params.cache
                            }
                            results.collect { r -> instances.find { i -> r[0] == i.id } }
                        }

                        listOrderByLatestReview { Map params = [:] ->
                            if (params == null) params = [:]
                            def clazz = delegate
                            def type = GrailsNameUtils.getPropertyName(clazz)
                            if (params.cache == null) params.cache = true
                            def results = clazz.executeQuery("select r.reviewRef from ReviewLink as r join r.review as review where r.type='$type' order by review.lastUpdated desc", params)
                            def instances = clazz.withCriteria {
                                inList 'id', results.collect { it[0] }
                                maxResults params.get("max") ?: 10
                                cache params.cache
                            }
                            results.collect { r -> instances.find { i -> r[0] == i.id } }
                        }

                        countReviews {->
                            def clazz = delegate
                            ReviewLink.createCriteria().get {
                                projections {
                                    countDistinct "reviewRef"
                                }
                                eq "type", GrailsNameUtils.getPropertyName(clazz)
                                cache true
                            }
                        }
                    }

                    review = { rater, Double starRating, String rTitle, String rBody ->
                        if (delegate.id == null) {
                            throw new ReviewException("You must save the entity [${delegate}] before calling review")
                        }
                        // try to find an existing review to update
                        def instance = delegate
                        def r = ReviewLink.createCriteria().get {
                            projections { property "review" }
                            review {
                                eq 'posterId', rater.id
                            }
                            eq "reviewRef", instance.id
                            eq "type", GrailsNameUtils.getPropertyName(instance.class)
                            cache true
                        }

                        // if there is no existing value, create a new one
                        if (!r) {
                            r = new Review(stars:starRating, posterId:rater.id, title: rTitle, body: rBody, posterClass:rater.class.name)
                            if (!r.validate()) {
                                throw new ReviewException("Cannot create review for args: [$rater, $starRating, $rTitle, $rBody], they are invalid. ")
                            }
                            r.save()
                            def link = new ReviewLink(review:r, reviewRef:delegate.id, type:GrailsNameUtils.getPropertyName(delegate.class))
                            link.save()
                        }
                        // for an existing review, just update the star value
                        else {
                            r.stars = starRating
                        }
                        return delegate
                    }

                    getReviews = { Map params = [:] ->
                        if (params == null) params = [:]
                        def instance = delegate
                        if (instance.id != null) {
                            ReviewLink.withCriteria(max: params.get("max") ?: 10, offset: params.get("offset") ?: 0) {
                                projections {
                                    property "review"
                                }
                                eq "reviewRef", instance.id
                                eq "type", GrailsNameUtils.getPropertyName(instance.class)
                                cache true
                            }
                        } else {
                            return Collections.EMPTY_LIST
                        }
                    }

                    getLatestReviews = { Map params = [:] ->
                        if (params == null) params = [:]
                        def instance = delegate
                        if (instance.id != null) {
                            ReviewLink.withCriteria(max: params.get("max") ?: 10, offset: params.get("offset") ?: 0) {
                                projections {
                                    property "review"
                                }
                                eq "reviewRef", instance.id
                                eq "type", GrailsNameUtils.getPropertyName(instance.class)
                                order("lastUpdated", "desc")
                                cache true
                            }
                        } else {
                            return Collections.EMPTY_LIST
                        }
                    }

                    getAverageReview = { ->
                        def instance = delegate
                        def result = ReviewLink.createCriteria().get {
                            review {
                                projections { avg 'stars' }
                            }
                            eq "reviewRef", instance.id
                            eq "type", GrailsNameUtils.getPropertyName(instance.class)
                            cache true
                        }
                        result
                    }

                    getTotalReviews = { ->
                        def instance = delegate
                        if (instance.id != null) {
                            ReviewLink.createCriteria().get {
                                projections {
                                    rowCount()
                                }
                                eq "reviewRef", instance.id
                                eq "type", GrailsNameUtils.getPropertyName(instance.class)
                                cache true
                            }
                        } else {
                            return 0
                        }
                    }

                    userReview = { user ->
                        if (!user) return
                        def instance = delegate
                        ReviewLink.withCriteria {
                            createAlias("review", "r")
                            projections {
                                property "review"
                            }
                            eq "reviewRef", instance.id
                            eq "type", GrailsNameUtils.getPropertyName(instance.class)
                            eq "r.posterId", user.id
                            cache true
                        }
                    }
                }
            }
        }
    }

    def doWithApplicationContext = { ctx ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    def onShutdown = { event ->
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
