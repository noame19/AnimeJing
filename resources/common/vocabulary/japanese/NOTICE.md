# AnimeJing JLPT vocabularies

These files were generated from **OpenJLPT** by evanclan,
https://github.com/evanclan/OpenJLPT , licensed under CC BY-SA 4.0.

AnimeJing ships the OpenJLPT vocab CSVs unchanged in `scripts/.cache/openjlpt/`
and writes MuJing-format JSON vocabularies (one per JLPT level) into this
directory. Total word count: 8,334 across N5 (662) / N4 (632) /
N3 (1784) / N2 (1793) / N1 (3463).

Optional jisho.org enrichment (kana / romaji / additional glosses) is
populated lazily by `com.mujingx.data.JishoClient` at runtime and cached
in `~/.cache/animejing/jisho.sqlite`.

If you redistribute AnimeJing, please retain attribution to
evanclan / OpenJLPT.
