package model

import androidx.annotation.Keep
import java.io.Serializable


@Keep
open class BaseResult(val code: Int? = 0, val message: String? = null) : Serializable {
    open fun resultOk(): Boolean {
        return code == 200
    }

    open fun isEmpty() = false
}