import masecla.reddit4j.client.Reddit4J
import masecla.reddit4j.client.UserAgentBuilder
import masecla.reddit4j.exceptions.AuthenticationException
import masecla.reddit4j.objects.RedditComment
import masecla.reddit4j.objects.RedditPost
import masecla.reddit4j.objects.subreddit.RedditSubreddit
import java.lang.IllegalArgumentException
import java.net.SocketException
import java.net.UnknownHostException
import kotlin.system.exitProcess

// These are used for creating the user agent.
private const val APP_NAME = "Reddit Reader"
private const val AUTHOR = "AUTHOR"
private const val VERSION = "0.1"

/**
 * A connection to the Reddit API for a specific user.
 */
object Connection {
    // don't need to worry about how these two statements work.
    private val userAgent = UserAgentBuilder().appname(APP_NAME).author(AUTHOR).version(VERSION).build()
    private val redditClient: Reddit4J = Reddit4J.rateLimited()

    init {
        try {
            redditClient.apply {
                username = USERNAME
                password = PASSWORD
                clientId = CLIENT_ID
                clientSecret = CLIENT_SECRET
                setUserAgent(userAgent)
                connect()
            }
        } catch (e: AuthenticationException) {
            println("Please check your credentials")
            exitProcess(1)
        } catch (e: UnknownHostException) {
            println("Please check your internet connection.")
            exitProcess(1)
        }
    }

    /**
     * This user's name (from their profile).
     */
    val userName: String
        get() = redditClient.selfProfile.name

    /**
     * Gets the subreddit named [subredditName].
     */
    fun getSubreddit(subredditName: String): RedditSubreddit {
        return redditClient.getSubreddit(subredditName)
    }

    /**
     * Gets posts from [subreddit].
     *
     * @return text posts that are marked as being acceptable
     * for people under 18
     */
    fun getPosts(subreddit: RedditSubreddit): List<RedditPost> =
        subreddit.hot.submit().filter { !it.isOver18 }.filter { it.selftext.isNotEmpty() }

    /**
     * Gets all comments for this [post].
     */
    fun getComments(post: RedditPost): List<RedditComment> {
        return redditClient.getCommentsForPost(post.subreddit, post.id).submit()
    }
}

/**
 * An option to present to the user.
 *
 * @property text a textual description
 * @property function the function to call if the option is selected
 */
class Option(val text: String, val function: () -> Unit) {
    companion object {
        /**
         * Offers the user [options] of what to do next. In addition to showing
         * the passed options, there is always an option numbered 0 to quit the
         * program and a final option to select a subreddit.
         */
        fun offerOptions(options: List<Option>) {
            val allOptions = listOf(
                Option("Quit", function = { exitProcess(0) })
            ) + options + listOf(
                Option("Select a subreddit", function = { selectSubreddit() })
            )

            while (true) {
                try {
                    println("Select an option: ")
                    for (i in allOptions.indices) {
                        println("\t$i. ${allOptions[i].text}")
                    }
                    val input = readln().toInt()
                    allOptions[input].function()
                    break
                } catch (e: NumberFormatException) {
                    println("Please use and type only numbers")
                } catch (e: IndexOutOfBoundsException) {
                    println("Please type in the range of 0 to ${allOptions.size - 1}")
                } catch (e: IllegalArgumentException) {
                    println("There is no subreddit with that name")
                }
            }
        }

        private fun showPostAuthor(posts: List<RedditPost>, postNumber: Int) {
            println("Post author: ${posts[postNumber].author}")

            val nextPost = Option("Show next post", function = { showPost(posts, postNumber + 1) })
            val listOfOptions = mutableListOf(
                Option("Show post again", function = { showPost(posts, postNumber) }),
                Option("Check for comments", function = { checkForComments(posts, postNumber) }),
            ).apply {
                if (postNumber + 1 < posts.size) {
                    add(nextPost)
                }
            }

            offerOptions(
                listOfOptions
            )
        }

        private fun checkForComments(posts: List<RedditPost>, postNumber: Int) {
            val options = mutableListOf(
                Option("Show post author", function = { showPostAuthor(posts, postNumber) }),

                ).apply {
                if (postNumber + 1 < posts.size) {
                    add(Option("Show next post", function = { showPost(posts, postNumber + 1) }))
                }
            }

            val comments: List<RedditComment> = Connection.getComments(posts[postNumber])
            println(
                when (comments.size) {
                    0 -> "There are no comments for this post."
                    1 -> "There is one comment for this post."
                    else -> "There are ${comments.size} comments for this post."
                }
            )
            if (comments.isNotEmpty()) {
                options.add(0, Option("Show first comment", function = { showComment(posts, postNumber, comments, 0) }))
            }
            offerOptions(options)
        }

        private fun displayPost(post: RedditPost) {
            println(post.title.uppercase())
            println()
            println(post.selftext)
            println()
        }

        private fun showPost(posts: List<RedditPost>, postNumber: Int) {
            displayPost(posts[postNumber])

            val options = mutableListOf(
                Option("Show post author", function = { showPostAuthor(posts, postNumber) }),
                Option("Check for comments", function = { checkForComments(posts, postNumber) }),
            ).apply {
                if (postNumber + 1 < posts.size) {
                    add(Option("Show next post", function = { showPost(posts, postNumber + 1) }))
                }
            }
            offerOptions(
                options
            )
        }

        private fun showComment(
            posts: List<RedditPost>, postNumber: Int, comments: List<RedditComment>, commentNumber: Int
        ) {
            println("Comment: ${comments[commentNumber].body}")

            // only if variables
            val nextPost = Option("Show next post", function = { showPost(posts, postNumber + 1) })
            val nextComment =
                Option("Show next comment", function = { showComment(posts, postNumber, comments, commentNumber + 1) })

            // always an option
            val listOfOptions = mutableListOf(
                Option(
                    "Show comment author",
                    function = { showCommentAuthor(posts, postNumber, comments, commentNumber) }),
                Option("Show post again", function = { showPost(posts, postNumber) }),

                ).apply {
                if (postNumber + 1 < posts.size) {
                    add(nextPost)
                }
                if (commentNumber + 1 < comments.size) {
                    add(nextComment)
                }
            }

            offerOptions(
                listOfOptions
            )

        }

        private fun showCommentAuthor(
            posts: List<RedditPost>,
            postNumber: Int,
            comments: List<RedditComment>,
            commentNumber: Int
        ) {
            println("Post author: ${comments[commentNumber].author}")

            val nextPost = Option("Show next post", function = { showPost(posts, postNumber + 1) })
            val nextComment =
                Option("Show next comment", function = { showComment(posts, postNumber, comments, commentNumber + 1) })

            // always an option
            val listOfOptions = mutableListOf(
                Option("Show post again", function = { showPost(posts, postNumber) }),

                ).apply {
                if (postNumber + 1 < posts.size) {
                    add(nextPost)
                }
                if (commentNumber + 1 < comments.size) {
                    add(nextComment)
                }
            }

            offerOptions(
                listOfOptions
            )

        }

        private fun quit() {
            println("Goodbye.")
            exitProcess(0)
        }

        /**
         * It is a helper function for plurality
         */
        private fun postsPlural(numPosts: List<RedditPost>): String {
            return when (numPosts.size) {
                0 -> "There are no posts."
                1 -> "There is one post."
                else -> "There are ${numPosts.size} posts."
            }
        }

        private fun selectSubreddit() {

            while (true) {
                try {
                    println("What subreddit would you like to select? ")
                    val subredditName = readln()
                    val subreddit: RedditSubreddit = Connection.getSubreddit(subredditName)
                    println("You are now in ${subreddit.displayName}.")

                    val posts = Connection.getPosts(subreddit)
                    println(postsPlural(posts))

                    if (posts.isNotEmpty()) {
                        offerOptions(
                            listOf(
                                Option("Show first post", function = { showPost(posts, 0) }),
                            )
                        )
                    }
                    break
                } catch (e: SocketException) {
                    println("Network is unreachable.")
                    offerOptions(
                        listOf(
                            Option("Retry", function = { selectSubreddit() }),
                        )
                    )
                }
            }


        }
    }
}

fun main() {
    println("Hello, ${Connection.userName}.")
    Option.offerOptions(emptyList())
}
