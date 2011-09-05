///////////////////////////////////////////////////////////////////////////////
// FILE:          OpenSPIM.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   The drivers for the OpenSPIM project
//                Based on the CDemoStage and CDemoXYStage classes
//                
// AUTHOR:        Johannes Schindelin
//
// COPYRIGHT:     Johannes Schindelin, 2011
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#ifndef _OPENSPIM_H_
#define _OPENSPIM_H_

#include "../../MMDevice/DeviceBase.h"

//////////////////////////////////////////////////////////////////////////////
// CSIABZStage class
//////////////////////////////////////////////////////////////////////////////

class CSIABTwister: public CStageBase<CSIABTwister>
{
public:
    CSIABTwister();
    ~CSIABTwister();

    bool Busy();
    double GetDelayMs() const;
    void SetDelayMs(double delay);
    bool UsesDelay();
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;

	int SetPositionUm(double pos);
	int SetRelativePositionUm(double d);
	int Move(double velocity);
	int SetAdapterOriginUm(double d);
	int GetPositionUm(double& pos);
	int SetPositionSteps(long steps);
	int GetPositionSteps(long& steps);
	int SetOrigin();
	int GetLimits(double& lower, double& upper);
	int IsStageSequenceable(bool& isSequenceable) const;
	int GetStageSequenceMaxLength(long& nrEvents) const;
	int StartStageSequence() const;
	int StopStageSequence() const;
	int ClearStageSequence();
	int AddToStageSequence(double position);
	int SendStageSequence() const; 
	bool IsContinuousFocusDrive() const;
};

//////////////////////////////////////////////////////////////////////////////
// CSIABZStage class
//////////////////////////////////////////////////////////////////////////////

class CSIABStage : public CStageBase<CSIABStage>
{
public:
    CSIABStage();
    ~CSIABStage();

    bool Busy();
    double GetDelayMs() const;
    void SetDelayMs(double delay);
    bool UsesDelay();
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;

	int SetPositionUm(double pos);
	int SetRelativePositionUm(double d);
	int Move(double velocity);
	int SetAdapterOriginUm(double d);
	int GetPositionUm(double& pos);
	int SetPositionSteps(long steps);
	int GetPositionSteps(long& steps);
	int SetOrigin();
	int GetLimits(double& lower, double& upper);
	int IsStageSequenceable(bool& isSequenceable) const;
	int GetStageSequenceMaxLength(long& nrEvents) const;
	int StartStageSequence() const;
	int StopStageSequence() const;
	int ClearStageSequence();
	int AddToStageSequence(double position);
	int SendStageSequence() const; 
	bool IsContinuousFocusDrive() const;
};

//////////////////////////////////////////////////////////////////////////////
// CSIABDemoStage class
// Simulation of the single axis stage
//////////////////////////////////////////////////////////////////////////////

class CSIABXYStage : public CXYStageBase<CSIABXYStage>
{
public:
    CSIABXYStage();
    ~CSIABXYStage();

    bool Busy();
    double GetDelayMs() const;
    void SetDelayMs(double delay);
    bool UsesDelay();
	int Initialize();
	int Shutdown();
	void GetName(char* name) const;

	int SetPositionUm(double x, double y);
	int SetRelativePositionUm(double dx, double dy);
	int SetAdapterOriginUm(double x, double y);
	int GetPositionUm(double& x, double& y);
	int GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax);
	int Move(double vx, double vy);

	int SetPositionSteps(long x, long y);
	int GetPositionSteps(long& x, long& y);
	int SetRelativePositionSteps(long x, long y);
	int Home();
	int Stop();
	int SetOrigin();//jizhen, 4/12/2007
	int GetStepLimits(long& xMin, long& xMax, long& yMin, long& yMax);
	double GetStepSizeXUm();
	double GetStepSizeYUm();
};

#endif //_OPENSPIM_H_

