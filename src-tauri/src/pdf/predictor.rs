/// Implements the Paeth predictor function from the PNG specification (RFC 2083 §6.6).
///
/// Given three neighboring pixel bytes (`a` = left, `b` = above, `c` = upper-left),
/// returns the one closest to the linear predictor `p = a + b - c`. Used by
/// [`decode_png_predictor`] for filter type 4.
pub(crate) fn paeth_predictor(a: u8, b: u8, c: u8) -> u8 {
    let a_i = a as i16;
    let b_i = b as i16;
    let c_i = c as i16;

    let p = a_i + b_i - c_i;
    let pa = (p - a_i).abs();
    let pb = (p - b_i).abs();
    let pc = (p - c_i).abs();

    if pa <= pb && pa <= pc {
        a
    } else if pb <= pc {
        b
    } else {
        c
    }
}

/// Decodes raw image data that uses PNG-style row filters (predictor values 10–14).
///
/// PDF streams with `/Predictor` ≥ 10 in their `/DecodeParms` embed per-row filter
/// bytes identical to those in the PNG format. This function reverses the filtering
/// to recover the original pixel data.
///
/// # Arguments
/// * `columns` - Number of pixel columns (image width).
/// * `colors` - Number of color components per pixel (1 for gray, 3 for RGB, 4 for CMYK).
/// * `bits_per_component` - Bit depth per component (typically 8).
/// * `data` - The filtered byte stream, where each row is prefixed by a 1-byte filter type.
///
/// # Returns
/// `Some(Vec<u8>)` containing the unfiltered pixel data, or `None` if the input
/// length is invalid or a row slice is out of bounds.
pub(crate) fn decode_png_predictor(
    columns: usize,
    colors: usize,
    bits_per_component: usize,
    data: &[u8],
) -> Option<Vec<u8>> {
    let bytes_per_pixel = (colors * bits_per_component).div_ceil(8);
    let row_len = (columns * colors * bits_per_component).div_ceil(8);
    let predictor_row_len = row_len + 1;

    if data.is_empty() || !data.len().is_multiple_of(predictor_row_len) {
        return None;
    }

    let rows = data.len() / predictor_row_len;
    let mut decompressed = vec![0u8; rows * row_len];

    for r in 0..rows {
        let row_start = r * predictor_row_len;
        let filter_type = data[row_start];
        let mut raw_row = vec![0u8; row_len];
        
        let src_end = row_start + predictor_row_len;
        if src_end <= data.len() {
            raw_row.copy_from_slice(&data[row_start + 1..src_end]);
        } else {
            return None;
        }

        let prev_row_start = if r > 0 { Some((r - 1) * row_len) } else { None };

        match filter_type {
            0 => { // None
                // raw_row is correct as is
            }
            1 => { // Sub
                for i in 0..row_len {
                    let left = if i >= bytes_per_pixel { raw_row[i - bytes_per_pixel] } else { 0 };
                    raw_row[i] = raw_row[i].wrapping_add(left);
                }
            }
            2 => { // Up
                if let Some(prev) = prev_row_start {
                    for i in 0..row_len {
                        let up = decompressed[prev + i];
                        raw_row[i] = raw_row[i].wrapping_add(up);
                    }
                }
            }
            3 => { // Average
                for i in 0..row_len {
                    let left = if i >= bytes_per_pixel { raw_row[i - bytes_per_pixel] } else { 0 };
                    let up = if let Some(prev) = prev_row_start { decompressed[prev + i] } else { 0 };
                    raw_row[i] = raw_row[i].wrapping_add(((left as u16 + up as u16) / 2) as u8);
                }
            }
            4 => { // Paeth
                for i in 0..row_len {
                    let left = if i >= bytes_per_pixel { raw_row[i - bytes_per_pixel] } else { 0 };
                    let up = if let Some(prev) = prev_row_start { decompressed[prev + i] } else { 0 };
                    let corner = if i >= bytes_per_pixel {
                        if let Some(prev) = prev_row_start { decompressed[prev + i - bytes_per_pixel] } else { 0 }
                    } else { 0 };

                    raw_row[i] = raw_row[i].wrapping_add(paeth_predictor(left, up, corner));
                }
            }
            _ => {
                // Unsupported filter type, use original row content
            }
        }

        let dest_start = r * row_len;
        decompressed[dest_start..dest_start + row_len].copy_from_slice(&raw_row);
    }

    Some(decompressed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_paeth_predictor() {
        assert_eq!(paeth_predictor(0, 0, 0), 0);
        assert_eq!(paeth_predictor(10, 20, 30), 10); // p=0, pa=10, pb=20, pc=30 -> a
        assert_eq!(paeth_predictor(50, 150, 100), 100); // p=100, pa=50, pb=50, pc=0 -> c
        assert_eq!(paeth_predictor(200, 10, 50), 200);
    }

    #[test]
    fn test_decode_png_predictor_none() {
        // filter=0, data=[10, 20, 30]
        // row_len = 3 (3 cols, 1 color, 8 bit)
        let data = vec![0, 10, 20, 30];
        let decoded = decode_png_predictor(3, 1, 8, &data).unwrap();
        assert_eq!(decoded, vec![10, 20, 30]);
    }

    #[test]
    fn test_decode_png_predictor_sub() {
        // filter=1, data=[10, 20, 30]
        // bytes_per_pixel = 1.
        // decoded should be: 10, 10+20=30, 30+30=60
        let data = vec![1, 10, 20, 30];
        let decoded = decode_png_predictor(3, 1, 8, &data).unwrap();
        assert_eq!(decoded, vec![10, 30, 60]);
    }

    #[test]
    fn test_decode_png_predictor_up() {
        // 2 rows, filter=2 (Up) for second row
        // Row 1: filter=0, [10, 20]
        // Row 2: filter=2, [5, 15]
        // decoded row 2: [10+5=15, 20+15=35]
        let data = vec![
            0, 10, 20,
            2, 5, 15
        ];
        let decoded = decode_png_predictor(2, 1, 8, &data).unwrap();
        assert_eq!(decoded, vec![10, 20, 15, 35]);
    }

    #[test]
    fn test_decode_png_predictor_invalid_length() {
        let data = vec![0, 10]; // Missing bytes for row
        assert_eq!(decode_png_predictor(2, 1, 8, &data), None);
    }
}
