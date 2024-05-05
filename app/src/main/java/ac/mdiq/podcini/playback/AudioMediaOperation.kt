package ac.mdiq.podcini.playback

import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

// converted to Kotlin from the java file: https://gist.github.com/DrustZ/d3d3fc8fcc1067433db4dd3079f8d187
object AudioMediaOperation {
    fun MergeAudios(selection: Array<String>, outpath: String?, callback: OperationCallbacks?) {
        var RECORDER_SAMPLERATE = 0
        try {
            val amplifyOutputStream = DataOutputStream(BufferedOutputStream(FileOutputStream(outpath)))
            val mergeFilesStream = arrayOfNulls<DataInputStream>(selection.size)
            val sizes = LongArray(selection.size)
            for (i in selection.indices) {
                val file = File(selection[i])
                sizes[i] = (file.length() - 44) / 2
            }
            for (i in selection.indices) {
                mergeFilesStream[i] = DataInputStream(BufferedInputStream(FileInputStream(selection[i])))

                if (i == selection.size - 1) {
                    mergeFilesStream[i]!!.skip(24)
                    val sampleRt = ByteArray(4)
                    mergeFilesStream[i]!!.read(sampleRt)
                    val bbInt = ByteBuffer.wrap(sampleRt).order(ByteOrder.LITTLE_ENDIAN)
                    RECORDER_SAMPLERATE = bbInt.getInt()
                    mergeFilesStream[i]!!.skip(16)
                } else {
                    mergeFilesStream[i]!!.skip(44)
                }
            }

            for (b in selection.indices) {
                for (i in 0 until sizes[b].toInt()) {
                    val dataBytes = ByteArray(2)
                    try {
                        dataBytes[0] = mergeFilesStream[b]!!.readByte()
                        dataBytes[1] = mergeFilesStream[b]!!.readByte()
                    } catch (e: EOFException) {
                        amplifyOutputStream.close()
                    }
                    val dataInShort = ByteBuffer.wrap(dataBytes).order(ByteOrder.LITTLE_ENDIAN).getShort()
                    val dataInFloat = dataInShort.toFloat() / 37268.0f

                    val outputSample = (dataInFloat * 37268.0f).toInt().toShort()
                    val dataFin = ByteArray(2)
                    dataFin[0] = (outputSample.toInt() and 0xff).toByte()
                    dataFin[1] = ((outputSample.toInt() shr 8) and 0xff).toByte()
                    amplifyOutputStream.write(dataFin, 0, 2)
                }
            }
            amplifyOutputStream.close()
            for (i in selection.indices) {
                mergeFilesStream[i]!!.close()
            }
        } catch (e: FileNotFoundException) {
            callback?.onAudioOperationError(e)
            e.printStackTrace()
        } catch (e: IOException) {
            callback?.onAudioOperationError(e)
            e.printStackTrace()
        }
        var size: Long = 0
        try {
            val fileSize = FileInputStream(outpath)
            size = fileSize.channel.size()
            fileSize.close()
        } catch (e1: FileNotFoundException) {
            // TODO Auto-generated catch block
            e1.printStackTrace()
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }

        val RECORDER_BPP = 16

        val datasize = size + 36
        val byteRate = ((RECORDER_BPP * RECORDER_SAMPLERATE) / 8).toLong()
        val longSampleRate = RECORDER_SAMPLERATE.toLong()
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (datasize and 0xffL).toByte()
        header[5] = ((datasize shr 8) and 0xffL).toByte()
        header[6] = ((datasize shr 16) and 0xffL).toByte()
        header[7] = ((datasize shr 24) and 0xffL).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = 1.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xffL).toByte()
        header[25] = ((longSampleRate shr 8) and 0xffL).toByte()
        header[26] = ((longSampleRate shr 16) and 0xffL).toByte()
        header[27] = ((longSampleRate shr 24) and 0xffL).toByte()
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = ((byteRate shr 8) and 0xffL).toByte()
        header[30] = ((byteRate shr 16) and 0xffL).toByte()
        header[31] = ((byteRate shr 24) and 0xffL).toByte()
        header[32] = ((RECORDER_BPP) / 8).toByte() // block align
        header[33] = 0
        header[34] = RECORDER_BPP.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (size and 0xffL).toByte()
        header[41] = ((size shr 8) and 0xffL).toByte()
        header[42] = ((size shr 16) and 0xffL).toByte()
        header[43] = ((size shr 24) and 0xffL).toByte()

        // out.write(header, 0, 44);
        try {
            val rFile = RandomAccessFile(outpath, "rw")
            rFile.seek(0)
            rFile.write(header)
            rFile.close()
            callback?.onAudioOperationFinished()
        } catch (e: FileNotFoundException) {
            // TODO Auto-generated catch block
            callback?.onAudioOperationError(e)
            e.printStackTrace()
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            callback?.onAudioOperationError(e)
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    fun RawToWave(rawfn: String?, wavefn: String?) {
        val rawFile = File(rawfn)
        val waveFile = File(wavefn)
        val rawData = ByteArray(rawFile.length().toInt())
        var input: DataInputStream? = null
        try {
            input = DataInputStream(FileInputStream(rawFile))
            input.read(rawData)
        } finally {
            input?.close()
        }

        val SAMPLE_RATE = 24000

        var output: DataOutputStream? = null
        try {
            output = DataOutputStream(FileOutputStream(waveFile))
            // WAVE header
            // see http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
            writeString(output, "RIFF") // chunk id
            writeInt(output, 36 + rawData.size) // chunk size
            writeString(output, "WAVE") // format
            writeString(output, "fmt ") // subchunk 1 id
            writeInt(output, 16) // subchunk 1 size
            writeShort(output, 1.toShort()) // audio format (1 = PCM)
            writeShort(output, 1.toShort()) // number of channels
            writeInt(output, SAMPLE_RATE) // sample rate
            writeInt(output, SAMPLE_RATE * 2) // byte rate
            writeShort(output, 2.toShort()) // block align
            writeShort(output, 16.toShort()) // bits per sample
            writeString(output, "data") // subchunk 2 id
            writeInt(output, rawData.size) // subchunk 2 size
            // Audio data (conversion big endian -> little endian)
            val shorts = ShortArray(rawData.size / 2)
            ByteBuffer.wrap(rawData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()[shorts]
            val bytes = ByteBuffer.allocate(shorts.size * 2)
            for (s in shorts) {
                bytes.putShort(s)
            }

            output.write(fullyReadFileToBytes(rawFile))
        } finally {
            output?.close()
        }
    }


    @Throws(IOException::class)
    fun fullyReadFileToBytes(f: File): ByteArray {
        val size = f.length().toInt()
        val bytes = ByteArray(size)
        val tmpBuff = ByteArray(size)
        val fis = FileInputStream(f)
        try {
            var read = fis.read(bytes, 0, size)
            if (read < size) {
                var remain = size - read
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain)
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read)
                    remain -= read
                }
            }
        } catch (e: IOException) {
            throw e
        } finally {
            fis.close()
        }

        return bytes
    }

    @Throws(IOException::class)
    fun writeInt(output: DataOutputStream, value: Int) {
        output.write(value shr 0)
        output.write(value shr 8)
        output.write(value shr 16)
        output.write(value shr 24)
    }

    @Throws(IOException::class)
    fun writeShort(output: DataOutputStream, value: Short) {
        output.write(value.toInt() shr 0)
        output.write(value.toInt() shr 8)
    }

    @Throws(IOException::class)
    fun writeString(output: DataOutputStream, value: String) {
        for (i in 0 until value.length) {
            output.write(value[i].code)
        }
    }

    interface OperationCallbacks {
        fun onAudioOperationFinished()
        fun onAudioOperationError(e: Exception?)
    }
}