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

import javax.servlet.http.HttpServletResponse

class ReviewableController {


    def add() {
        def poster = evaluatePoster()
        def reviewLink
        try {
            if (params['review'] instanceof Map) {
                Review.withTransaction { status ->
                    def review = new Review(params['review'])
                    review.posterId = poster.id
                    review.posterClass = poster.class.name
                    reviewLink = new ReviewLink(params['reviewLink'])
                    reviewLink.type = GrailsNameUtils.getPropertyName(reviewLink.type)

                    if (!review.save()) {
                        status.setRollbackOnly()
                    }
                    else {
                        reviewLink.review = review
                        if (!reviewLink.save()) status.setRollbackOnly()
                    }
                }
            }
        }
        catch (Exception e) {
            log.error "Error posting review: ${e.message}"
        }
        log.debug("review page URI: " + params.reviewPageURI)
        redirect url: params.reviewPageURI
    }

    def update() {
        def poster = evaluatePoster()
        def reviewLink
        try {
            if (params['review'] instanceof Map) {
                Review.withTransaction { status ->
                    // TODO get existing review from database
                    def review = new Review(params['review'])
                    review.posterId = poster.id
                    review.posterClass = poster.class.name
                    reviewLink = new ReviewLink(params['reviewLink'])
                    reviewLink.type = GrailsNameUtils.getPropertyName(reviewLink.type)

                    if (!review.save()) {
                        status.setRollbackOnly()
                    }
                    else {
                        reviewLink.review = review
                        if (!reviewLink.save()) status.setRollbackOnly()
                    }
                }
            }
        }
        catch (Exception e) {
            log.error "Error posting review: ${e.message}"
        }

        if (request.xhr || params.async) {

            def reviews = CommentLink.createCriteria().list(max: 1, offset: 0) {
                projections {
                    property "review"
                }
                eq 'type', reviewLink.type
                eq 'reviewRef', reviewLink.reviewRef
                review {
                    eq 'posterId', poster.id
                }
                cache true
            }

            def noEscape = false
            plugin.isAvailable(name: 'grails-ui') { noEscape = true }
            // TODO decide if I want to use templates
            render template: "/commentable/comment",
                    plugin: "commentable",
                    collection: comments,
                    var: "comment",
                    model: [noEscape: noEscape]
        }
        else {
            redirect url: params.reviewPageURI
        }
    }

    def delete() {
        def review = Review.get(params.id)

        if (review) {
            CommentLink.findAllByReview(review).each {
                it.delete()
            }
            review.delete()

            if (params.reviewPageURI) {
                redirect url: params.reviewPageURI
            } else {
                render status: HttpServletResponse.SC_OK
            }
        } else {
            render status: HttpServletResponse.SC_NOT_FOUND
        }
    }

    private evaluatePoster() {
        def evaluator = grailsApplication.config.grails.reviewable.poster.evaluator
        def poster
        if (evaluator instanceof Closure) {
            evaluator.delegate = this
            evaluator.resolveStrategy = Closure.DELEGATE_ONLY
            poster = evaluator.call()
        }

        if (!poster) {
            throw new ReviewException("No [grails.reviewable.poster.evaluator] setting defined or the evaluator doesn't evaluate to an entity. Please define the evaluator correctly in grails-app/conf/Config.groovy or ensure reviewing is secured via your security rules")
        }
        if (!poster.id) {
            throw new ReviewException("The evaluated Review poster is not a persistent instance.")
        }
        return poster
    }
}