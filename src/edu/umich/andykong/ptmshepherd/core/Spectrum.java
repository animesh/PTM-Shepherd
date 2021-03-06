package edu.umich.andykong.ptmshepherd.core;

import edu.umich.andykong.ptmshepherd.PTMShepherd;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

public class Spectrum implements Comparable<Spectrum> {

	int scanNum, charge;
	double precursorMass, rt;
	double monoMass, targetMass;
	float [] peakMZ;
	float [] peakInt;
	double norm;
	public String scanName;
	public int msLevel;
	
	static float [] fact;

	//this constructor parses batmass compatible filetypes
	public Spectrum(int nfrags) {
		peakMZ = new float[nfrags];
		peakInt = new float[nfrags];
		norm = -1;
		if(fact == null) {
			fact = new float[128];
			fact[0] = 0;
			for(int i = 1; i < 128; i++)
				fact[i] = (float)(fact[i-1] + Math.log(i));
		}
	}

	//this constructor parses MZBin files
	public Spectrum(String scanname, int scannum, int z, int mslevel, double precursormass, double rettime, float[] peakmz, float[] peakint) {
		scanName = scanname;
		scanNum = scannum;
		charge = z;
		precursorMass = precursormass;
		rt = rettime;
		peakMZ = peakmz;
		peakInt = peakint;
		msLevel = mslevel;
		norm = -1;
		if(fact == null) {
			fact = new float[128];
			fact[0] = 0;
			for(int i = 1; i < 128; i++)
				fact[i] = (float)(fact[i-1] + Math.log(i));
		}
	}

	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(scanNum+" -");
		for(int i = 0; i < peakMZ.length; i++)
			sb.append(String.format(" [%.2f %.2f]",peakMZ[i],peakInt[i]));
		return sb.toString();
	}
	
	public double getSimilarity(Spectrum s, double ppmTol) {
		double sum = 0;
		double ppm = ppmTol*(1.0/1000000);
		int pos = 0;
		for(int i = 0; i < peakMZ.length; i++) {
			for(int j = pos; j < s.peakMZ.length; j++) {
				if(s.peakMZ[j] < peakMZ[i]*(1-ppm))
					pos = j;
				else if(s.peakMZ[j] > peakMZ[i]*(1+ppm))
					break;
				else {
					sum += peakInt[i]*s.peakInt[j];
				}
			}
		}
		return sum;
	}
	
	public double cosineSimilarity(Spectrum s, double ppmTol) {
		double n1 = norm(ppmTol);
		double n2 = s.norm(ppmTol);
		return (getSimilarity(s, ppmTol) / (n1 * n2));
	}
	
	public double averageSimilarity(Collection<Spectrum> specs, double ppmTol) {
		double sum = 0;
		for(Spectrum s : specs) {
			if (s == null) {
				continue;
			}
			sum += cosineSimilarity(s, ppmTol);
		}
		return sum / specs.size();
	}
	
	public double norm(double ppmTol) {
		if(norm >= 0)
			return norm;
		norm = Math.sqrt(getSimilarity(this, ppmTol));
		return norm;
	}
	
	public void condition(int topN, double ratio) {
		ArrayList<Peak> peaks = new ArrayList<Peak>();
		float mv = 0;
		for(int i = 0 ;i < peakInt.length; i++) {  
			peaks.add(new Peak(peakMZ[i],peakInt[i]));
			if(peakInt[i] > mv)
				mv = peakInt[i];
		}
				
		Collections.sort(peaks, new Comparator<Peak>() {
			public int compare(Peak o1, Peak o2) {
				return -1*Float.valueOf(o1.Int).compareTo(o2.Int);
			}
		});
		
		while(peaks.size() > topN)
			peaks.remove(peaks.size()-1);
		while(peaks.size() > 0 && (peaks.get(peaks.size()-1).Int < peaks.get(0).Int*ratio))
			peaks.remove(peaks.size()-1);
		
		Collections.sort(peaks, new Comparator<Peak>() {
			public int compare(Peak o1, Peak o2) {
				return Float.valueOf(o1.MZ).compareTo(o2.MZ);
			}
		});
		
		peakMZ = new float[peaks.size()];
		peakInt = new float[peaks.size()];
		for(int i = 0; i < peaks.size(); i++) {
			peakMZ[i] = peaks.get(i).MZ;
			peakInt[i] = (100*peaks.get(i).Int) / mv;
		}
	}
	
	public float getHyper(String seq, float [] mods, double ppmTol) {
		float score = 0.0f;
		float iB = 0.0f, iY = 0.0f;
		int nB = 0, nY = 0;
		int maxCharge = (charge==2)?1:2;
		
		float [] aaMasses = AAMasses.monoisotopic_masses;
		float cM = 0;
		double tol;
		int fP = 0;
		int cLen = seq.length();
		for(int ccharge = 1; ccharge <= maxCharge; ccharge++) {
			double cmass = AAMasses.monoisotopic_nterm_mass;
			fP = 0;
			for (int i = 0; i < cLen - 1; i++) {
				cmass += (aaMasses[seq.charAt(i) - 'A']+mods[i]) / ccharge;
				tol = cmass * (ppmTol/1000000.0);
				while(fP < peakMZ.length && peakMZ[fP] < (cmass - tol))
					fP++;
				if(fP < peakMZ.length && Math.abs(peakMZ[fP] - cmass) < tol) {
					cM = peakInt[fP];
				} else 
					cM = 0;
				if(cM > 0) {
					nB++;
					iB += cM;
				}
			}
		}
		for(int ccharge = 1; ccharge <= maxCharge; ccharge++) {
			double cmass = (AAMasses.monoisotopic_cterm_mass + (ccharge+1)*AAMasses.monoisotopic_nterm_mass) / ccharge;
			fP = 0;
			for (int i = 0; i < cLen - 1; i++) {
				cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i])/ ccharge;
				tol = cmass * (ppmTol/1000000.0);
				while(fP < peakMZ.length && peakMZ[fP] < (cmass - tol))
					fP++;
				if(fP < peakMZ.length && Math.abs(peakMZ[fP] - cmass) < tol) {
					cM = peakInt[fP];
				} else 
					cM = 0;
				if(cM > 0) {
					nY++;
					iY += cM;
				}
			}
		}
		
		score = fact[nB] + fact[nY];
		if(iB > 1)
			score += Math.log(iB);
		if(iY > 1)
			score += Math.log(iY);
		
		return score;
	}
	
	public int getFrags(String seq, float [] mods, double ppmTol) {
		float iB = 0.0f, iY = 0.0f;
		int nB = 0, nY = 0;
		int maxCharge = (charge==2)?1:2;
		
		float [] aaMasses = AAMasses.monoisotopic_masses;
		float cM = 0;
		double tol;
		int fP = 0;
		int cLen = seq.length();
		for(int ccharge = 1; ccharge <= maxCharge; ccharge++) {
			double cmass = AAMasses.monoisotopic_nterm_mass;
			fP = 0;
			for (int i = 0; i < cLen - 1; i++) {
				cmass += (aaMasses[seq.charAt(i) - 'A']+mods[i]) / ccharge;
				tol = cmass * (ppmTol/1000000.0);
				while(fP < peakMZ.length && peakMZ[fP] < (cmass - tol))
					fP++;
				if(fP < peakMZ.length && Math.abs(peakMZ[fP] - cmass) < tol) {
					cM = peakInt[fP];
				} else 
					cM = 0;
				if(cM > 0) {
					nB++;
					iB += cM;
				}
			}
		}
		for(int ccharge = 1; ccharge <= maxCharge; ccharge++) {
			double cmass = (AAMasses.monoisotopic_cterm_mass + (ccharge+1)*AAMasses.monoisotopic_nterm_mass) / ccharge;
			fP = 0;
			for (int i = 0; i < cLen - 1; i++) {
				cmass += (aaMasses[seq.charAt(cLen - 1 - i) - 'A'] + mods[cLen - 1 - i])/ ccharge;
				tol = cmass * (ppmTol/1000000.0);
				while(fP < peakMZ.length && peakMZ[fP] < (cmass - tol))
					fP++;
				if(fP < peakMZ.length && Math.abs(peakMZ[fP] - cmass) < tol) {
					cM = peakInt[fP];
				} else 
					cM = 0;
				if(cM > 0) {
					nY++;
					iY += cM;
				}
			}
		}
		
		return nB+nY;
	}

	public int compareTo(Spectrum o) {
		return scanNum - o.scanNum;
	}
}

class Peak {
	float MZ, Int;
	public Peak(float MZ, float Int) {
		this.MZ = MZ;
		this.Int = Int;
	}
}
